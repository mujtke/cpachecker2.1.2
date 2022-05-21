// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lockOld;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.TrivialApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisTM;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.cpa.lockOld.AbstractLockState;
import org.sosy_lab.cpachecker.cpa.lockOld.DeadLockState;
import org.sosy_lab.cpachecker.cpa.lockOld.LockPrecision;
import org.sosy_lab.cpachecker.cpa.lockOld.LockReducer;
import org.sosy_lab.cpachecker.cpa.lockOld.LockState;
import org.sosy_lab.cpachecker.cpa.lockOld.LockStopOperator;
import org.sosy_lab.cpachecker.cpa.lockOld.LockTransferRelation;

@Options(prefix = "cpa.lockOld")
public class LockCPA extends AbstractCPA
    implements ConfigurableProgramAnalysisWithBAM, StatisticsProvider,
    ConfigurableProgramAnalysisTM {

  public static enum LockAnalysisMode {
    RACE,
    DEADLOCK
  }

  public static enum StopMode {
    DEFAULT,
    EMPTYLOCKSET
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(LockCPA.class);
  }

  @Option(description = "What are we searching for: race or deadlock", secure = true)
  private LockAnalysisMode analysisMode = LockAnalysisMode.RACE;

  @Option(description = "Consider or not special cases with empty lock sets", secure = true)
  private StopMode stopMode = StopMode.DEFAULT;

  @Option(description = "Consider or not lock guards", secure = true)
  private boolean considerLockGuards = true;

  @Option(description = "Enable refinement procedure", secure = true)
  private boolean refinement = false;

  @Option(
    secure = true,
    name = "merge",
    toUppercase = true,
    values = {"SEP", "JOIN"},
    description = "which merge operator to use for LockCPA")
  private String mergeType = "SEP";

  private final LockReducer reducer;

  private LockCPA(Configuration config, LogManager logger) throws InvalidConfigurationException {
    super(
        DelegateAbstractDomain.<AbstractLockState>getInstance(),
        new LockTransferRelation(config, logger));
    config.inject(this);
    reducer = new LockReducer(config);
  }

  @Override
  public AbstractState getInitialState(CFANode node, StateSpacePartition pPartition) {
    switch (analysisMode) {
      case RACE:
        return new LockState();

      case DEADLOCK:
        return new DeadLockState();

      default:
        // The analysis should fail at CPA creation
        throw new UnsupportedOperationException("Unsupported analysis mode");
    }
  }

  @Override
  public Precision getInitialPrecision(CFANode node, StateSpacePartition pPartition)
      throws InterruptedException {
    if (refinement) {
      return new LockPrecision();
    } else {
      return super.getInitialPrecision(node, pPartition);
    }
  }

  @Override
  public MergeOperator getMergeOperator() {
    switch (mergeType) {
      case "SEP":
        return MergeSepOperator.getInstance();

      case "JOIN":
        return new MergeJoinOperator(getAbstractDomain());

      default:
        // The analysis should fail at CPA creation
        throw new UnsupportedOperationException("Unsupported merge type");
    }
  }

  @Override
  public StopOperator getStopOperator() {
    switch (stopMode) {
      case DEFAULT:
        return buildStopOperator("SEP");

      case EMPTYLOCKSET:
        return new LockStopOperator();

      default:
        // The analysis should fail at CPA creation
        throw new UnsupportedOperationException("Unsupported stop mode");
    }
  }

  @Override
  public Reducer getReducer() {
    return reducer;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    LockTransferRelation transfer = (LockTransferRelation) getTransferRelation();
    pStatsCollection.add(transfer.getStatistics());
    reducer.collectStatistics(pStatsCollection);
  }

  @Override
  public ApplyOperator getApplyOperator() {
    if (considerLockGuards) {
      return new LockApplyOperator();
    } else {
      return TrivialApplyOperator.getInstance();
    }
  }
}
