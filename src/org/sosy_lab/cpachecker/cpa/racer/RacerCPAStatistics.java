// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import java.io.PrintStream;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.bam.BAMMultipleCEXSubgraphComputer;
import org.sosy_lab.cpachecker.cpa.lockOld.LockTransferRelation;
import org.sosy_lab.cpachecker.cpa.racer.errorPrinter.RacerETVErrorTracePrinter;
import org.sosy_lab.cpachecker.cpa.racer.errorPrinter.RacerErrorTracePrinter;
import org.sosy_lab.cpachecker.cpa.racer.errorPrinter.RacerKlever3ErrorTracePrinter;
import org.sosy_lab.cpachecker.cpa.racer.errorPrinter.RacerKleverErrorTracePrinter;
import org.sosy_lab.cpachecker.cpa.racer.errorPrinter.RacerKleverErrorTracePrinterOld;
import org.sosy_lab.cpachecker.cpa.racer.RacerState;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.racer")
public class RacerCPAStatistics implements Statistics {

  public enum OutputFileType {
    ETV,
    KLEVER,
    KLEVER3,
    KLEVER_OLD
  }

  private final LogManager logger;

  private final Configuration config;
  private final LockTransferRelation lockTransfer;
  private RacerErrorTracePrinter errPrinter;
  private final CFA cfa;

  private BAMMultipleCEXSubgraphComputer computer;

  final StatTimer transferRelationTimer = new StatTimer("Time for transfer relation");
  final StatTimer transferForEdgeTimer = new StatTimer("Time for transfer for edge");
  final StatTimer innerStopTimer = new StatTimer("Time for inner stop operators");
  final StatTimer innerAnalysisTimer = new StatTimer("Time for inner analyses");
  final StatTimer bindingTimer = new StatTimer("Time for binding computation");
  final StatTimer checkForSkipTimer = new StatTimer("Time for checking edge for skip");
  final StatTimer precTimer = new StatTimer("Time for precision adjustment");
  final StatTimer mergeTimer = new StatTimer("Time for merge operator");
  final StatTimer stopTimer = new StatTimer("Time for stop operator");
  final StatTimer racerStopTimer = new StatTimer("Time for racerStop operator");
  final StatTimer wrappedStopTimer = new StatTimer("Time for wrappedStop operator");
  private final StatTimer printUnsafesTimer = new StatTimer("Time for unsafes printing");
  private final StatTimer printStatisticsTimer = new StatTimer("Time for printing statistics");


  @Option(
      name = "printUnsafesIfUnknown",
      description = "print found unsafes in case of unknown verdict",
      secure = true)
  private boolean printUnsafesInCaseOfUnknown = true;

  @Option(
      name = "outputType",
      description = "all variables should be printed to the one file or to the different",
      secure = true
  )
  private OutputFileType outputFileType = OutputFileType.KLEVER;

  public RacerCPAStatistics(
      Configuration pConfig, LogManager pLogger, CFA pCfa, LockTransferRelation pLockTransfer)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    logger = pLogger;
    config = pConfig;
    cfa = pCfa;
    lockTransfer = pLockTransfer;
  }

  @Override
  public void printStatistics(
      // TODO not accomplished
      PrintStream out, Result result, UnmodifiableReachedSet reached) {
    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(out);
    writer.put(transferRelationTimer)
        .beginLevel()
        .put(transferForEdgeTimer)
        .beginLevel()
        .put(checkForSkipTimer)
        .put(bindingTimer)
        .put(innerAnalysisTimer)
        .endLevel()
        .endLevel()
        .put(precTimer)
        .put(mergeTimer)
        .put(stopTimer)
        .beginLevel()
        .put(racerStopTimer)
        .put(innerStopTimer)
        .endLevel();

    ReachedSet reachedSet = (ReachedSet) reached;
    while (reachedSet instanceof ForwardingReachedSet) {
      reachedSet = ((ForwardingReachedSet) reachedSet).getDelegate();
    }
    RacerUsageReachedSet uReached = (RacerUsageReachedSet) reachedSet;

    if (printUnsafesInCaseOfUnknown || result != Result.UNKNOWN) {
      printUnsafesTimer.start();
      try {
        switch (outputFileType) {
          case KLEVER3:
            errPrinter = new RacerKlever3ErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          case KLEVER:
            errPrinter = new RacerKleverErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          case KLEVER_OLD:
            errPrinter =
                new RacerKleverErrorTracePrinterOld(config, computer, cfa, logger, lockTransfer);
            break;
          case ETV:
            errPrinter = new RacerETVErrorTracePrinter(config, computer, cfa, logger, lockTransfer);
            break;
          default:
            throw new UnsupportedOperationException("Unknown type " + outputFileType);
        }
        errPrinter.printErrorTraces(uReached);
        errPrinter.printStatistics(writer);
      } catch (InvalidConfigurationException e) {
        logger.log(Level.SEVERE, "Cannot create error trace printer: " + e.getMessage());
      }
      printUnsafesTimer.stop();
    }

    printStatisticsTimer.start();
    RacerState.get(reached.getFirstState()).getStatistics().printStatistics(writer);
    uReached.printStatistics(writer);
    writer.put(printUnsafesTimer);
    printStatisticsTimer.stop();
    writer.put(printStatisticsTimer);
  }

  @Override
  public @Nullable String getName() {
    return "RacerCPAStatistics";
  }
}
