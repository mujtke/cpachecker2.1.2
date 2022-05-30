// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.lockOld.LockCPA;
import org.sosy_lab.cpachecker.cpa.lockOld.LockTransferRelation;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.RacerIdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.RegionBasedIdentifierCreator;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;

@Options(prefix = "cpa.racer")
public class RacerUsageCPA extends AbstractSingleWrapperCPA implements StatisticsProvider {

  private final RacerStopOperator stopOperator;
  private final RacerMergeOperator mergeOperator;
  private final RacerTransferRelation transferRelation;
  private final RacerPrecisionAdjustment precisionAdjustment;
  private final Reducer reducer;
  private final RacerCPAStatistics statistics;
  private final CFA cfa;
  private final LogManager logger;
  private final Map<CFANode, Map<GeneralIdentifier, DataType>> localMap;
  private final RacerUsageProcessor usageProcessor;
  private final RacerIdentifierCreator creator;
  private final ShutdownNotifier shutdownNotifier;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(RacerUsageCPA.class);
  }

  @Option(
      description = "use sound regions as identifiers",
      secure = true)
  private boolean useSoundRegions = false;

  @Option(description = "A path to precision", name = "precision.path", secure = true)
  @FileOption(Type.OUTPUT_FILE)
  private Path outputFileName = Path.of("localsave");

  @Option(
      description = "bind arguments of functions with passed variables",
      secure = true)
  private boolean bindArgsFunctions = false;

  @Option(
      description = "do not use initial variable in analysis if found an alias for it",
      secure = true)
  private boolean filterAliases = true;

  private RacerUsageCPA(
      ConfigurableProgramAnalysis pCpa, CFA pCfa,
      ShutdownNotifier pShutdownNotifier,LogManager pLogger, Configuration pConfig)
      throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this);
    cfa = pCfa;
    LockCPA lockCPA = CPAs.retrieveCPA(this, LockCPA.class);
    statistics = new RacerCPAStatistics(pConfig, pLogger, pCfa,
        lockCPA != null ? (LockTransferRelation) lockCPA.getTransferRelation() : null);
    stopOperator = new RacerStopOperator(pCpa.getStopOperator(), statistics);
    mergeOperator = new RacerMergeOperator(pCpa.getMergeOperator(), statistics, bindArgsFunctions);
    Optional<VariableClassification> varClassification = pCfa.getVarClassification();
    if (useSoundRegions) {
      creator = new RegionBasedIdentifierCreator(varClassification);
    } else {
      creator = new RacerIdentifierCreator();
    }
    transferRelation = new RacerTransferRelation(pCpa.getTransferRelation(), pConfig, pLogger, statistics, bindArgsFunctions, creator);
    precisionAdjustment = new RacerPrecisionAdjustment(pCpa.getPrecisionAdjustment(), statistics);

    RacerPrecisionParser parser = new RacerPrecisionParser(cfa, pLogger);
    localMap = parser.parse(outputFileName);
    usageProcessor = new RacerUsageProcessor(
        pConfig,
        pLogger,
        localMap,
        transferRelation.getBinderFunctionInfo(),
        creator);
    // here without BAM
    reducer = null;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return DelegateAbstractDomain.getInstance();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public MergeOperator getMergeOperator() {
//    return mergeOperator;
    return getWrappedCpa().getMergeOperator();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  public Reducer getReducer() {
    return reducer;
  }

  public Precision getInitialPresion(CFANode pNode, StateSpacePartition p)
  throws InterruptedException {
    RacerPrecisionParser parser = new RacerPrecisionParser(cfa, logger);
    return RacerPrecision.create(getWrappedCpa().getInitialPrecision(pNode, p), parser.parse(outputFileName));
  }

  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
    throws InterruptedException {
    if (filterAliases) {
      return RacerState.createInitialState(getWrappedCpa().getInitialState(pNode, pPartition));
    } else {
      return RacerStateConservative.createInitialState(getWrappedCpa().getInitialState(pNode, pPartition));
    }
  }

  public RacerCPAStatistics getStats() { return statistics; }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
    super.collectStatistics(pStatsCollection);
  }

  public RacerUsageProcessor getUsageProcessor() {
    return usageProcessor;
  }

  public ShutdownNotifier getNotifier() {
    return shutdownNotifier;
  }
}
