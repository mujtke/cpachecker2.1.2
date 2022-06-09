// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.Racer;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import java.io.PrintStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdateListener;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdater;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.racer.RacerUsageReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class Plan_C_Algorithm implements Algorithm, StatisticsProvider, ReachedSetUpdater {

  /* define a debug flag */
  public static boolean __DEBUG__ = false;

  private static class Plan_C_Statistics implements Statistics {

    private static Timer totalTimer = new Timer();

    @Override
    public void printStatistics(
        PrintStream out, Result result, UnmodifiableReachedSet reached) {

    }

    @Override
    public @Nullable String getName() {
      return "Plan_C_algorithm";
    }
  }

  private final Plan_C_Statistics stats = new Plan_C_Statistics();

  private final List<ReachedSetUpdateListener> reachedSetUpdateListeners = new CopyOnWriteArrayList<>();

  @Options(prefix = "Plan_C_Algorithm")
  public static class Plan_C_AlgorithmFactory implements AlgorithmFactory {

    private final AlgorithmFactory algorithmFactory;
    private final LogManager logger;

    public Plan_C_AlgorithmFactory(
        Algorithm pAlgorithm,
        ConfigurableProgramAnalysis pCpa,
        LogManager pLogger,
        Configuration pConfig,
        ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
     this(() -> pAlgorithm, pCpa, pLogger, pConfig, pShutdownNotifier);
    }

    public Plan_C_AlgorithmFactory(
        AlgorithmFactory pAlgorithmFactory,
        ConfigurableProgramAnalysis pCpa,
        LogManager pLogger,
        Configuration pConfig,
        ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {
      pConfig.inject(this);
      algorithmFactory = pAlgorithmFactory;
      logger = pLogger;
    }

    @Override
    public Plan_C_Algorithm newInstance() {
      return new Plan_C_Algorithm(
          algorithmFactory.newInstance(), logger);
    }
  }

  private final LogManager logger;
  private final Algorithm algorithm;

  private Plan_C_Algorithm(
      Algorithm pAlgorithm,
      LogManager pLogger) {
    algorithm = pAlgorithm;
    logger = pLogger;
  }

  @Override
  public AlgorithmStatus run(ReachedSet reached) throws CPAException, InterruptedException {
    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    final boolean _debug_ = __DEBUG__;
    int locationLoop = 1;
    long plancAlgorithmTime = System.currentTimeMillis();
    stats.totalTimer.start();
    try {

      boolean raceFound;

      do {
        raceFound = false;

        status = status.update(algorithm.run(reached));

        System.out.println("\u001b[31miteration " + locationLoop++ + "\u001b[0m");
        if (_debug_) {
          System.out.println("\u001b[31miteration " + locationLoop++ + "\u001b[0m");
        }
        if (((RacerUsageReachedSet)reached).newSuccessorsInEachIteration.isEmpty()) {
          // 新的一轮计算中没有产生后继状态

          if (reached.hasWaitingState()) {
            // 还有后继没有被探索
            if (_debug_) {
              System.out.println("no successors got but still have wait state");
            }
            continue;
          } else {
            // 可达图探索完成

            if (((RacerUsageReachedSet)reached).isStillHaveCoveredStates()) {
              // 如果存在covered的状态
              // 现将covered的状态全部放回waitlist中
              ((RacerUsageReachedSet)reached).rollbackCoveredStates();
              if (_debug_) {
                System.out.println("\u001b[34mclear visited locations\u001b[0m");
              }
              continue;
            } else {
              // 没有covered的状态，结束整个计算
              break;
            }
          }
        }

        notifyReachedSetUpdateListeners(reached);

        if (haveUnsafe(reached)) {
          // TODO 在newSuccessorsInEachIteration中寻找race
          ((RacerUsageReachedSet)reached).setHaveUnsafes(true);
          break;
        }

        // 将新产生的后继的location放到RacerUsageReachedSet中的visitedLocations中
//        Set<AbstractState> newGottenLocs = ((RacerUsageReachedSet)reached).newSuccessorsInEachIteration.keySet();
//        Iterator<AbstractState> it = newGottenLocs.iterator();
//        while (it.hasNext()) {
//          AbstractState p = it.next();
//          AbstractStateWithLocations stateWithLocations = extractStateByType(p, AbstractStateWithLocations.class);
//          for (CFANode node : stateWithLocations.getLocationNodes()) {
//            RacerUsageReachedSet.visitedLocations.add(node);
//          }
//        }

        // TODO 在下一次计算后继时，清空上一次计算得到的后继
        ((RacerUsageReachedSet)reached).newSuccessorsInEachIteration.clear();

      } while (!raceFound);
    } finally {
//      System.out.println("total time Plan_C algorithm: " + (System.currentTimeMillis() - plancAlgorithmTime) + " ms");
      stats.totalTimer.stop();
      if (_debug_) {
        System.out.println("Total time for Plan_C algorithm: " + stats.totalTimer);
        System.out.println("reached set size: " + reached.size());
        System.out.println("last state id: " + ((ARGState)(reached.getLastState())).getStateId());
      }
    }
    return status;
  }

  private boolean haveUnsafe(ReachedSet pReached) {
    // be here, the newSuccessorsInEachIteration should be not empty, else be error
    return ((RacerUsageReachedSet)pReached).haveUnsafeInNewSucs();
  }

  @Override
  public void register(ReachedSetUpdateListener pReachedSetUpdateListener) {
    if (algorithm instanceof ReachedSetUpdater) {
      ((ReachedSetUpdater) algorithm).register(pReachedSetUpdateListener);
    }
    reachedSetUpdateListeners.add(pReachedSetUpdateListener);
  }

  @Override
  public void unregister(ReachedSetUpdateListener pReachedSetUpdateListener) {
    if (algorithm instanceof ReachedSetUpdater) {
      ((ReachedSetUpdater) algorithm).unregister(pReachedSetUpdateListener);
    }
    reachedSetUpdateListeners.remove(pReachedSetUpdateListener);
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    if (algorithm instanceof StatisticsProvider) {
      ((StatisticsProvider) algorithm).collectStatistics(statsCollection);
    }
    statsCollection.add(stats);
  }

  private void notifyReachedSetUpdateListeners(ReachedSet pReachedSet) {
    for (ReachedSetUpdateListener rsul : reachedSetUpdateListeners) {
      rsul.updated(pReachedSet);
    }
  }
}
