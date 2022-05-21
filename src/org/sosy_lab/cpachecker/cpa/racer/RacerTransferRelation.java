// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.usage.VariableSkipper;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.RacerIdentifierCreator;

@Options(prefix = "cpa.racer")
public class RacerTransferRelation extends AbstractSingleWrapperTransferRelation {
  private RacerCPAStatistics statistics;

  @Option(description = "functions, which we don't analize", secure = true)
  private Set<String> skippedfunctions = ImmutableSet.of();

  @Option(
      description =
          "functions, which are used to bind variables (like list elements are binded to list"
              + " variable)",
      secure = true)
  private Set<String> binderFunctions = ImmutableSet.of();

  @Option(description = "functions, which are marked as write access", secure = true)
  private Set<String> writeAccessFunctions = ImmutableSet.of();

  @Option(name = "abortfunctions", description = "functions, which stops analysis", secure = true)
  private Set<String> abortFunctions = ImmutableSet.of();

  private final VariableSkipper varSkipper;
  private final Map<String, BinderFunctionInfo> binderFunctionInfo;
  private final boolean bindArgsFunctions;
  private final RacerIdentifierCreator creator;

  private final LogManager logger;

  private RacerState newState;
  private RacerPrecision precision;

  public RacerTransferRelation(
      TransferRelation pWrappedTransfer,
      Configuration pconfig,
      LogManager pLogger,
      RacerCPAStatistics pStatistics,
      boolean pBindArgsFunctions,
      RacerIdentifierCreator c)
      throws InvalidConfigurationException {
    super(pWrappedTransfer);
    pconfig.inject(this, RacerTransferRelation.class);
    statistics = pStatistics;
    logger = pLogger;
    ImmutableMap.Builder<String, BinderFunctionInfo> binderFunctionInfoBuilder = ImmutableMap.builder();
    from(binderFunctions).forEach(name -> binderFunctionInfoBuilder.put(name, new BinderFunctionInfo(name, pconfig, logger)));
    BinderFunctionInfo dummy = new BinderFunctionInfo();
    from(writeAccessFunctions).forEach(name -> binderFunctionInfoBuilder.put(name, dummy));
    binderFunctionInfo = binderFunctionInfoBuilder.buildOrThrow();

    // BinderFunctions should not be analysed
    skippedfunctions = Sets.union(skippedfunctions, binderFunctions);
    varSkipper = new VariableSkipper(pconfig);
    bindArgsFunctions = pBindArgsFunctions;
    creator = c;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      // TODO not accomplished
      AbstractState pElement, Precision pPrecision) throws CPATransferException, InterruptedException {

      Collection<AbstractState> results;

      statistics.transferRelationTimer.start();

      CompositeState compositeState = (CompositeState) ((RacerState)pElement).getWrappedState();
      AbstractStateWithLocations locStateBefore = extractStateByType(compositeState, AbstractStateWithLocations.class);
      assert locStateBefore != null : "get AbstractStateWithLocations error!";
      results = new ArrayList<>();

      Collection<AbstractState> successors = ImmutableList.copyOf(transferRelation.getAbstractSuccessors(compositeState, pPrecision));

      // 将compositeState转换为RacerState的过程
      Set<CFAEdge> edges = new HashSet<>();
      Set<CFANode> locsBefore = new HashSet<>();  // 传入状态中所包含的location集合
      for (CFANode node : locStateBefore.getLocationNodes()) { locsBefore.add(node); }
      for (AbstractState state : successors) {    // 对每个后继先计算边，再利用边将状态转换成usageState
        CompositeState successor = (CompositeState) state;
        AbstractStateWithLocations locStateAfter = extractStateByType(successor, AbstractStateWithLocations.class);
        Set<CFANode> locsAfter = new HashSet<>();   // 新获取的状态中所包含的location集合
        for (CFANode node : locStateAfter.getLocationNodes()) { locsAfter.add(node); }
        locsAfter.removeAll(locsBefore);
        for (CFANode node : locsAfter) {    // 将产生的新后继的状态所对应的边都加到edges中（按道理，一个状态只对应一条边才对）
          for (int i = 0; i < node.getNumEnteringEdges(); i++) { edges.add(node.getEnteringEdge(i)); }
        }

        Set<AbstractState> racerStates = new HashSet<>();
        statistics.bindingTimer.start();
        for (CFAEdge edge : edges) {

          statistics.checkForSkipTimer.start();
          CFAEdge currentEdge = changeIfNeccessary(edge); //如果pCfaEdge的调用了abort函数或者skipped函数，则返回空边或者summary边（相当于跳过函数分析）
          statistics.checkForSkipTimer.stop();
          if (currentEdge == null) { // 如果调用了abort函数，则后继状态为空集
            continue;
          }

          creator.setCurrentFunction(getCurrentFunction((RacerState) pElement, currentEdge));
          RacerState oldState = (RacerState) pElement;    // oldState对应传入的旧状态
          Collection<AbstractState> successorsNew = new ArrayList<>();
          successorsNew.add(state);

          racerStates.addAll(handleEdge(currentEdge, successorsNew, oldState));
        }
        statistics.bindingTimer.stop();
        for (AbstractState rState : racerStates) {
          results.add(rState);
        }
        edges.clear();
      }

      statistics.transferRelationTimer.stop();
      return results;
    }

    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
        // TODO not accomplished
        AbstractState state, Precision pPrecision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
      return null;
    }

  private CFAEdge changeIfNeccessary(CFAEdge pCfaEdge) {
    /**
     * 获取边的后继，如果后继是函数调用边
     * case1：调用的函数是abortFunction，则返回NULL
     * case2：...的函数是skippedFunction，则返回summary边，相当于函数被跳过
     * 如果不是函数调用边，则返回传入的边，相当于该边会被用于后面的分析
     */
    if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
      String functionName = pCfaEdge.getSuccessor().getFunctionName();

      if (abortFunctions.contains(functionName)) {
        return null;
      } else if (skippedfunctions.contains(functionName)) {
        CFAEdge newEdge = ((FunctionCallEdge) pCfaEdge).getSummaryEdge();
        logger.log(Level.FINEST, functionName + " will be skipped");
        return newEdge;
      }
    }
    return pCfaEdge;
  }

  /**
   * 将计算产生的后继状态(compositeState)转换成UsageState
   * @param pCfaEdge
   * @param newWrappedStates
   * @param oldState
   * @return
   * @throws CPATransferException
   */
  private Collection<? extends AbstractState>
  handleEdge(
      CFAEdge pCfaEdge,
      Collection<? extends AbstractState> newWrappedStates,
      RacerState oldState)
      throws CPATransferException {

    Collection<AbstractState> result = new ArrayList<>();

    switch (pCfaEdge.getEdgeType()) {
      case StatementEdge:
      case FunctionCallEdge:
      {     //这里应该是对应没有skipped的函数调用

        CStatement stmt;
        if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {  //表达式边，应该是类似"a = foo()"之类的
          CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
          stmt = statementEdge.getStatement();
        } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {   //函数调用边，应该类似"func()"之类的
          // TODO 不确定
          stmt = (CStatement) ((CFunctionCallEdge) pCfaEdge).getRawAST().get();
          //stmt = ((CFunctionCallEdge) pCfaEdge).getRawAST().get();
        } else {
          // Not sure what is it
          break;
        }

        Collection<Pair<AbstractIdentifier, AbstractIdentifier>> newLinks = ImmutableSet.of();

        if (stmt instanceof CFunctionCallAssignmentStatement) {     // 函数调用赋值表达式
          // assignment like "a = b" or "a = foo()"
          CAssignment assignment = (CAssignment) stmt;
          CFunctionCallExpression right =
              ((CFunctionCallAssignmentStatement) stmt).getRightHandSide();   // RHS
          CExpression left = assignment.getLeftHandSide();      // LHS
          newLinks =
              handleFunctionCallExpression(
                  left,
                  right,
                  (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge && bindArgsFunctions));   //进行绑定分析
        } else if (stmt instanceof CFunctionCallStatement) {
          /*
           * Body of the function called in StatementEdge will not be analyzed and thus there is no
           * need binding its local variables with its arguments.
           * 如果只是函数调用而没有LSH，则不用进行绑定分析
           */
          newLinks =
              handleFunctionCallExpression(
                  null,
                  ((CFunctionCallStatement) stmt).getFunctionCallExpression(),
                  (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge && bindArgsFunctions));
        }

        // Do not know why, but replacing the loop into lambda greatly decreases the speed
        for (AbstractState newWrappedState : newWrappedStates) {  //这里的WrappedState是compositeState，WrappedStates是WrappedState的数组
          RacerState newhandledState = oldState.copy(newWrappedState);

          if (!newLinks.isEmpty()) {
            newhandledState = newhandledState.put(newLinks);
          }

          result.add(newhandledState);
        }
        return ImmutableList.copyOf(result);      //UsageState的内容：stats（数据相关）、variableBindingRelation、WrappedStates（compositeState）
      }

      case FunctionReturnEdge: {
        // Data race detection in recursive calls does not work because of this optimisation
        CFunctionReturnEdge returnEdge = (CFunctionReturnEdge) pCfaEdge;
        String functionName =
            returnEdge.getSummaryEdge()
                .getExpression()
                .getFunctionCallExpression()
                .getDeclaration()
                .getName();
        for (AbstractState newWrappedState : newWrappedStates) {
          RacerState newhandledState = oldState.copy(newWrappedState);
          result.add(newhandledState.removeInternalLinks(functionName));   //去除与局部变量相关的变量绑定
        }
        return ImmutableList.copyOf(result);
      }

      case AssumeEdge:
      case DeclarationEdge:
      case ReturnStatementEdge:
      case BlankEdge:
      case CallToReturnEdge:
      {
        break;
      }

      default:
        throw new UnrecognizedCFAEdgeException(pCfaEdge);
    }
    for (AbstractState newWrappedState : newWrappedStates) {
      result.add(oldState.copy(newWrappedState));
    }
    return ImmutableList.copyOf(result);
  }

  private Collection<Pair<AbstractIdentifier, AbstractIdentifier>> handleFunctionCallExpression(
      final CExpression left,
      final CFunctionCallExpression fcExpression,
      boolean bindArgs) {                                                               // 变量绑定分析

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();

    if (binderFunctionInfo.containsKey(functionCallName)) {                             //
      BinderFunctionInfo bInfo = binderFunctionInfo.get(functionCallName);

      if (bInfo.shouldBeLinked()) {
        List<CExpression> params = fcExpression.getParameterExpressions();
        // Sometimes these functions are used not only for linkings.
        // For example, getListElement also deletes element.
        // So, if we can't link (no left side), we skip it
        //例如获取list的元素且不会删除元素时，就会执行linked操作，应该是将元素和list名字联系起来（应该对应了LHS存在的情况）。如果不存在LHS，则没有必要执行linked操作
        return bInfo.constructIdentifiers(left, params, creator);
      }
    }

    if (bindArgs) {         //如果不是变量绑定的函数
      if (fcExpression.getDeclaration() == null) {
        logger.log(Level.FINE, "No declaration.");
      } else {
        Collection<Pair<AbstractIdentifier, AbstractIdentifier>> newLinks = new ArrayList<>();
        for (int i = 0; i < fcExpression.getDeclaration().getParameters().size(); i++) {
          if (i >= fcExpression.getParameterExpressions().size()) {
            logger.log(Level.FINE, "More parameters in declaration than in expression.");
            break;
          }

          CSimpleDeclaration exprIn = fcExpression.getDeclaration().getParameters().get(i);
          CExpression exprFrom = fcExpression.getParameterExpressions().get(i);
          if (exprFrom.getExpressionType() instanceof CPointerType) {
            AbstractIdentifier idIn, idFrom;
            idFrom = creator.createIdentifier(exprFrom, 0);
            creator.setCurrentFunction(fcExpression.getFunctionNameExpression().toString());
            idIn = creator.createIdentifier(exprIn, 0);
            newLinks.add(Pair.of(idIn, idFrom));
          }
        }
        return ImmutableSet.copyOf(newLinks);
      }
    }
    return ImmutableSet.of();
  }

  private String getCurrentFunction(RacerState pState, CFAEdge pEdge) {
    // TODO not accomplished
    // return AbstractStates.extractStateByType(pState, Plan_C_threadingState.class).getCurrentFunction(pEdge);
    return null;
  }

  Map<String, BinderFunctionInfo> getBinderFunctionInfo() {
    return binderFunctionInfo;
  }
}
