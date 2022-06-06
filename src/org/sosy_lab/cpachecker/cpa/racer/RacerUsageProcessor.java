// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import java.util.Collection;
import java.util.Map;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.racerThreading.RacerThreadingState;
import org.sosy_lab.cpachecker.cpa.rcucpa.RCUState;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo.Access;
import org.sosy_lab.cpachecker.cpa.racer.VariableSkipper;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.RacerIdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class RacerUsageProcessor {

  private final Map<String, BinderFunctionInfo> binderFunctionInfo;

  private final LogManager logger;
  private final VariableSkipper varSkipper;

  private Map<CFANode, Map<GeneralIdentifier, DataType>> precision;
  private Map<CFAEdge, Collection<Pair<AbstractIdentifier, Access>>> usages;

  private final Collection<CFANode> uselessNodes;
  private Collection<SingleIdentifier> redundantIds;

  private RacerIdentifierCreator creator;

  StatTimer totalTimer = new StatTimer("Total time for usage processing");
  StatTimer localTimer = new StatTimer("Time for sharedness check");
  StatTimer usagePreparationTimer = new StatTimer("Time for usage preparation");
  StatTimer usageCreationTimer = new StatTimer("Time for usage creation");
  StatTimer searchingCacheTimer = new StatTimer("Time for searching in cache");

  public RacerUsageProcessor(
      Configuration pConfig,
      LogManager pLogger,
      Map<CFANode, Map<GeneralIdentifier, DataType>> pPrecision,
      Map<String, BinderFunctionInfo> pBinderFunctionInfo,
      RacerIdentifierCreator pCreator)
      throws InvalidConfigurationException {
    logger = pLogger;
    binderFunctionInfo = pBinderFunctionInfo;
    varSkipper = new VariableSkipper(pConfig);
    precision = pPrecision;
    uselessNodes = new IdentityHashSet<>();
    creator = pCreator;
    usages = new IdentityHashMap<>();
  }


  public void updateRedundantUnsafes(Set<SingleIdentifier> set) {
    redundantIds = set;
  }

  /**
   * threading方式下获取Usages
   * @param pState
   * @return 提取到的UsageInfo列表
   */
  public List<UsageInfo> getUsagesForStateByParentState(AbstractState pState) {

    // Not a set, as usage.equals do not consider id
    List<UsageInfo> result;
//     totalTimer.start();
    result = new ArrayList<>();

    AbstractStateWithLocations
        stateWithLocations = extractStateByType(pState, AbstractStateWithLocations.class);
    assert stateWithLocations != null : "NullPoint exception";
    List<CFANode> nodes = new ArrayList<>();
    for (CFANode node : stateWithLocations.getLocationNodes()) {
      if (uselessNodes.contains(node)) { continue; }     // 如果pState中所有的LocationNode都属于uselessNodes，则获取Usage的结果为空
      else nodes.add(node);
    }
    if (nodes.isEmpty()) {  // 如果都是无用节点
      return result;
    }
    //

    boolean resultComputed = false;
    List<UsageInfo> tmpResult = new ArrayList<>();
    CFANode currentEdgeNode = null;

    for (ARGState parentState : ((ARGState) pState).getParents()) {   // 获取Usage需要用到边
      CFAEdge edge = parentState.getEdgeToChild((ARGState)pState);
      if (edge != null) {    // 如果有CFA边连接
        currentEdgeNode = edge.getSuccessor();
        resultComputed = true;
        Collection<Pair<AbstractIdentifier, Access>> ids;

        if (usages.containsKey(edge)) {
          ids = usages.get(edge);     // 如果这条边已经在usages中了，则从usages中获取相应的<id, Access>对
        } else {                        // 如果这条边不在usages中，则进行新的计算
          //ids = getUsagesForEdge(parentState, edge);  // parentState为当前状态的父状态，edge为两者之间所连的边, 使用parentState作为keyState
          ids = getUsagesForEdge((ARGState)pState, edge);  // TODO: have done.改用当前状态作为keyState，而不是使用parentState作为keyState
          if (!ids.isEmpty()) {
            usages.put(edge, ids);  // 如果计算结果不为空，则将结果放入usages中
          }
        }

        // usagePreparationTimer.start();
        for (Pair<AbstractIdentifier, Access> pair : ids) {
          AbstractIdentifier id = pair.getFirst();
          // Links will be extracted as aliases later
          if (!redundantIds.contains(id)) {         // 如果id不是冗余的，冗余是指不重复？
            createUsages(id, edge.getSuccessor(), (ARGState)pState, pair.getSecond(), tmpResult);    // 将相应的Usage放入tmpResult中
          }
        }
        // usagePreparationTimer.stop();

      } else {
        // No edge, for example, due to BAM
        // Note, function call edge was already handled, we do not miss it
      }
      if (resultComputed && tmpResult.isEmpty() && currentEdgeNode != null) {
        uselessNodes.add(currentEdgeNode);         // 如果已经结算过但是result依然为空，将该State放入uselessNodes中
        for (int i = 0; i < currentEdgeNode.getNumEnteringEdges(); i++) {
          CFAEdge e = currentEdgeNode.getEnteringEdge(i);
          usages.remove(e);
        }
        resultComputed = false;
        currentEdgeNode = null;
      }
      if (!tmpResult.isEmpty()) {
        result.addAll(tmpResult);
      }
    }

//    totalTimer.stop();
    return result;
  }

  public Collection<Pair<AbstractIdentifier, Access>>
  getUsagesForEdge(AbstractState pChild, CFAEdge pCfaEdge) {

    switch (pCfaEdge.getEdgeType()) {
      case DeclarationEdge:
      {
        CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
        return handleDeclaration(pChild, declEdge);
      }

      // if edge is a statement edge, e.g. a = b + c
      case StatementEdge:
      {
        CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
        return handleStatement(pChild, statementEdge.getStatement(), pCfaEdge);
      }

      case AssumeEdge:
      {
        return visitStatement(
            pChild,
            ((CAssumeEdge) pCfaEdge).getExpression(),
            Access.READ, pCfaEdge);
      }

      case FunctionCallEdge:
      {
        return handleFunctionCall(pChild, (CFunctionCallEdge) pCfaEdge);
      }

      default:
      {
        return ImmutableSet.of();
      }
    }
  }

  private Collection<Pair<AbstractIdentifier, Access>>
  handleFunctionCall(AbstractState pChild, CFunctionCallEdge edge) {
    // TODO 这里的返回值是AstNode与CStatement有差别
    CStatement statement = (CStatement) edge.getRawAST().get();

    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CFunctionCallExpression right =
          ((CFunctionCallAssignmentStatement) statement).getRightHandSide();
      CExpression variable = ((CFunctionCallAssignmentStatement) statement).getLeftHandSide();

      List<Pair<AbstractIdentifier, Access>> internalIds = new ArrayList<>();
      internalIds.addAll(handleFunctionCallExpression(pChild, right, edge));
      internalIds.addAll(visitStatement(pChild, variable, Access.WRITE, edge));
      return ImmutableList.copyOf(internalIds);

    } else if (statement instanceof CFunctionCallStatement) {
      return handleFunctionCallExpression(
          pChild,
          ((CFunctionCallStatement) statement).getFunctionCallExpression(), edge);
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>>
  handleDeclaration(AbstractState pChild, CDeclarationEdge declEdge) {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      // not a variable declaration
      return ImmutableSet.of();
    }
    CVariableDeclaration decl = (CVariableDeclaration) declEdge.getDeclaration();

    if (decl.isGlobal()) {      // 全局变量的申明（即便在申明的同时初始化）不会引起竞争
      return ImmutableSet.of();
    }

    CInitializer init = decl.getInitializer();

    if (init == null) {
      // no assignment
      return ImmutableSet.of();
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression) init).getExpression();
      // Use EdgeType assignment for initializer expression to avoid mistakes related to expressions
      // "int CPACHECKER_TMP_0 = global;"
      return visitStatement(pChild, initExpression, Access.READ, declEdge);

      // We do not add usage for currently declared variable
      // It can not cause a race
      // 在某个线程中申明的局部变量不会引起竞争
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>> handleFunctionCallExpression(
      AbstractState pChild,
      final CFunctionCallExpression fcExpression, CFAEdge pCfaEdge) {

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();

    List<Pair<AbstractIdentifier, Access>> internalIds = new ArrayList<>();

    if (binderFunctionInfo.containsKey(functionCallName)) {
      BinderFunctionInfo currentInfo = binderFunctionInfo.get(functionCallName);
      List<CExpression> params = fcExpression.getParameterExpressions();

      AbstractIdentifier id;

      for (int i = 0; i < params.size(); i++) {
        id = currentInfo.createParamenterIdentifier(params.get(i), i, getCurrentFunction(pChild, pCfaEdge));
        internalIds.add(Pair.of(id, currentInfo.getBindedAccess(i)));
      }
    } else {

      fcExpression.getParameterExpressions()
          .forEach(p -> internalIds.addAll(visitStatement(pChild, p, Access.READ, pCfaEdge)));    // 处理函数表达式的参数
      internalIds
          .addAll(visitStatement(pChild, fcExpression.getFunctionNameExpression(), Access.READ, pCfaEdge));    // 处理函数表达式的函数名
    }
    return internalIds;
  }

  private Collection<Pair<AbstractIdentifier, Access>>
  handleStatement(AbstractState pChild, final CStatement pStatement, CFAEdge pCfaEdge) {

    if (pStatement instanceof CAssignment) {    // 如果是赋值表达式
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment) pStatement;
      CExpression left = assignment.getLeftHandSide();
      CRightHandSide right = assignment.getRightHandSide();
      List<Pair<AbstractIdentifier, Access>> internalIds = new ArrayList<>();

      if (right instanceof CExpression) {
        internalIds.addAll(visitStatement(pChild, (CExpression) right, Access.READ, pCfaEdge));     // 如果right是表达式（非函数调用），则对右边表达式中的变量进行的是read访问

      } else if (right instanceof CFunctionCallExpression) {
        internalIds.addAll(handleFunctionCallExpression(pChild, (CFunctionCallExpression) right, pCfaEdge));
      }
      internalIds.addAll(visitStatement(pChild, left, Access.WRITE, pCfaEdge));   // 对left进行的是write访问
      return ImmutableList.copyOf(internalIds);

    } else if (pStatement instanceof CFunctionCallStatement) {
      return handleFunctionCallExpression(
          pChild,
          ((CFunctionCallStatement) pStatement).getFunctionCallExpression(), pCfaEdge);

    } else if (pStatement instanceof CExpressionStatement) {
      return visitStatement(
          pChild,
          ((CExpressionStatement) pStatement).getExpression(),
          Access.WRITE, pCfaEdge);
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>>
  visitStatement(
      AbstractState pChild,
      final CExpression expression,
      final Access access,
      CFAEdge pCfaEdge) {
//        String fName = getCurrentFunction(pChild);  // 获取函数调用栈中Child所处的函数的函数名，例如: "caif_ser_init"
    String fName = getCurrentFunction(pChild, pCfaEdge);  // 获取函数调用栈中Child所处的函数的函数名，例如: "caif_ser_init"

    // To avoid synchronization
    RacerIdentifierCreator localCreator = creator.copy();
    localCreator.setCurrentFunction(fName);
    ExpressionHandler handler = new ExpressionHandler(access, fName, varSkipper, localCreator);//相当于访问者模式中的visitor
    expression.accept(handler);

    return handler.getProcessedExpressions();     // 返回handler中的result
  }

  private String getCurrentFunction(AbstractState pState, CFAEdge pCfaEdge) {
    return extractStateByType(pState, RacerThreadingState.class).getCurrentFunction(pCfaEdge);
  }

  private void createUsages(
      AbstractIdentifier pId,
      CFANode pNode,
      AbstractState pChild,
      Access pAccess,
      List<UsageInfo> result) {

    // TODO looks like a hack
    if (pId instanceof SingleIdentifier) {
      SingleIdentifier singleId = (SingleIdentifier) pId;
      if (singleId.getName().contains("CPAchecker_TMP")) {      // Id的name，即实际的变量名
        RCUState rcuState = extractStateByType(pChild, RCUState.class);
        if (rcuState != null) {
          singleId = (SingleIdentifier) rcuState.getNonTemporaryId(singleId);
        }
      }
    }

    Iterable<AliasInfoProvider> providers =
        AbstractStates.asIterable(pChild).filter(AliasInfoProvider.class);
    Set<AbstractIdentifier> aliases = new TreeSet<>();

    aliases.add(pId);
    for (AliasInfoProvider provider : providers) {
      aliases.addAll(provider.getAllPossibleAliases(pId));
    }

    for (AliasInfoProvider provider : providers) {
      provider.filterAliases(pId, aliases);
    }

    for (AbstractIdentifier aliasId : aliases) {
      createUsageAndAdd(aliasId, pNode, pChild, pAccess, result);
    }
  }

  private void createUsageAndAdd(
      AbstractIdentifier pId,
      CFANode pNode,
      AbstractState pChild,
      Access pAccess,
      List<UsageInfo> result) {

    if (pId instanceof LocalVariableIdentifier && !pId.isDereferenced()) {    //如果是局部变量并且没有被解引用，则result为空
      return;
    }

    // usageCreationTimer.start();
    UsageInfo usage = UsageInfo.createUsageInfo(pAccess, pChild, pId);
    // usageCreationTimer.stop();

    // Precise information, using results of shared analysis
    if (!usage.isRelevant()) {    //如果Usage为IRRELEVANT_USAGE，则result为空
      return;
    }

    // localTimer.start();
    Map<GeneralIdentifier, DataType> localInfo = precision.get(pNode);
    if (localInfo == null) {
      // No preset info, but there may be runtime info
      localInfo = new HashMap<>();
    }

    SingleIdentifier singleId = usage.getId();
    GeneralIdentifier gId = singleId.getGeneralId();
    if (localInfo.get(gId) == DataType.LOCAL) {
      logger.log(
          Level.FINER,
          singleId + " is considered to be local, so it wasn't add to statistics");
      // localTimer.stop();
      return;
    } else {

      Iterable<LocalInfoProvider> itStates =
          AbstractStates.asIterable(pChild).filter(LocalInfoProvider.class);
      boolean isLocal = false;
      boolean isGlobal = false;

      for (AbstractIdentifier id : singleId.getComposedIdentifiers()) {
        if (id instanceof SingleIdentifier) {
          GeneralIdentifier gcId = ((SingleIdentifier) id).getGeneralId();
          DataType type = localInfo.get(gcId);
          if (type == DataType.GLOBAL) {
            // Add global var to statistics in any case
            isGlobal = true;
            break;
          } else if (type == DataType.LOCAL) {
            isLocal = true;
          }
          for (LocalInfoProvider state : itStates) {
            isLocal |= state.isLocal(gcId);
          }
        }
      }
      for (LocalInfoProvider state : itStates) {
        isLocal |= state.isLocal(gId);
      }
      if (isLocal && !isGlobal) {
        logger.log(
            Level.FINER,
            singleId + " is supposed to be local, so it wasn't add to statistics");
        // localTimer.stop();
        return;
      }
    }

    // localTimer.stop();
    logger.log(Level.FINER, "Add " + usage + " to unsafe statistics");

    result.add(usage);
  }

  public void printStatistics(StatisticsWriter pWriter) {
    pWriter.put(totalTimer)
        .beginLevel()
        .put(usagePreparationTimer)
        .beginLevel()
        .put(searchingCacheTimer)
        .put(usageCreationTimer)
        .put(localTimer)
        .endLevel()
        .put("Number of useless nodes", uselessNodes.size())
        .put("Number of cached edges", usages.size());
  }
}