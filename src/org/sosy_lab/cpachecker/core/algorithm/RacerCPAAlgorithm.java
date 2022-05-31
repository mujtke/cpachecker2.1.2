// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.base.Functions;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.Racer.Plan_C_Algorithm;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ForcedCovering;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.racerThreading.RacerThreadingState;
import org.sosy_lab.cpachecker.cpa.arg.ARGMergeJoinCPAEnabledAnalysis;
import org.sosy_lab.cpachecker.cpa.racer.RacerUsageReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatValue;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatHist;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class RacerCPAAlgorithm implements Algorithm, StatisticsProvider {

  private static class CPAStatistics implements Statistics {

    private Timer totalTimer = new Timer();
    private Timer chooseTimer = new Timer();
    private Timer precisionTimer = new Timer();
    private Timer transferTimer = new Timer();
    private Timer mergeTimer = new Timer();
    private Timer stopTimer = new Timer();
    private Timer addTimer = new Timer();
    private Timer forcedCoveringTimer = new Timer();

    private int countIterations = 0;
    private int maxWaitlistSize = 0;
    private long countWaitlistSize = 0;
    private int countSuccessors = 0;
    private int maxSuccessors = 0;
    private int countMerge = 0;
    private int countStop = 0;
    private int countBreak = 0;

    private Map<String, AbstractStatValue> reachedSetStatistics = new HashMap<>();

    private void stopAllTimers() {
      totalTimer.stopIfRunning();
      chooseTimer.stopIfRunning();
      precisionTimer.stopIfRunning();
      transferTimer.stopIfRunning();
      mergeTimer.stopIfRunning();
      stopTimer.stopIfRunning();
      addTimer.stopIfRunning();
      forcedCoveringTimer.stopIfRunning();
    }

    private void updateReachedSetStatistics(Map<String, AbstractStatValue> newStatistics) {
      for (Entry<String, AbstractStatValue> e : newStatistics.entrySet()) {
        String key = e.getKey();
        AbstractStatValue val = e.getValue();
        if (!reachedSetStatistics.containsKey(key)) {
          reachedSetStatistics.put(key, val);
        } else {
          AbstractStatValue newVal = reachedSetStatistics.get(key);

          if (val == newVal) {
            // ignore, otherwise counters would double
          } else if (newVal instanceof StatCounter) {
            assert val instanceof StatCounter;
            ((StatCounter) newVal).mergeWith((StatCounter) val);
          } else if (newVal instanceof StatInt) {
            assert val instanceof StatInt;
            ((StatInt) newVal).add((StatInt) val);
          } else if (newVal instanceof StatHist) {
            assert val instanceof StatHist;
            ((StatHist) newVal).mergeWith((StatHist) val);
          } else {
            throw new AssertionError("Can't handle " + val.getClass().getSimpleName());
          }
        }
      }
    }

    @Override
    public String getName() {
      return "CPA algorithm";
    }

    @Override
    public void printStatistics(PrintStream out, Result pResult, UnmodifiableReachedSet pReached) {
      out.println("Number of iterations:            " + countIterations);
      if (countIterations == 0) {
        // Statistics not relevant, prevent division by zero
        return;
      }

      out.println("Max size of waitlist:            " + maxWaitlistSize);
      out.println("Average size of waitlist:        " + countWaitlistSize / countIterations);
      StatisticsWriter w = StatisticsWriter.writingStatisticsTo(out);
      for (AbstractStatValue c : reachedSetStatistics.values()) {
        w.put(c);
      }
      out.println("Number of computed successors:   " + countSuccessors);
      out.println("Max successors for one state:    " + maxSuccessors);
      out.println("Number of times merged:          " + countMerge);
      out.println("Number of times stopped:         " + countStop);
      out.println("Number of times breaked:         " + countBreak);
      out.println();
      out.println(
          "Total time for CPA algorithm:     "
              + totalTimer
              + " (Max: "
              + totalTimer.getMaxTime().formatAs(TimeUnit.SECONDS)
              + ")");
      out.println("  Time for choose from waitlist:  " + chooseTimer);
      if (forcedCoveringTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for forced covering:       " + forcedCoveringTimer);
      }
      out.println("  Time for precision adjustment:  " + precisionTimer);
      out.println("  Time for transfer relation:     " + transferTimer);
      if (mergeTimer.getNumberOfIntervals() > 0) {
        out.println("  Time for merge operator:        " + mergeTimer);
      }
      out.println("  Time for stop operator:         " + stopTimer);
      out.println("  Time for adding to reached set: " + addTimer);
    }
  }

  @Options(prefix = "cpa")
  public static class RacerCPAAlgorithmFactory implements AlgorithmFactory {

    @Option(
        secure = true,
        description = "Which strategy to use for forced coverings (empty for none)",
        name = "forcedCovering")
    @ClassOption(packagePrefix = "org.sosy_lab.cpachecker")
    private ForcedCovering.@Nullable Factory forcedCoveringClass = null;

    @Option(
        secure = true,
        description =
            "Do not report 'False' result, return UNKNOWN instead. "
                + " Useful for incomplete analysis with no counterexample checking.")
    private boolean reportFalseAsUnknown = false;

    private final ForcedCovering forcedCovering;

    private final ConfigurableProgramAnalysis cpa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    public RacerCPAAlgorithmFactory(
        ConfigurableProgramAnalysis pCpa,
        LogManager pLogger,
        Configuration config,
        ShutdownNotifier pShutdownNotifier)
        throws InvalidConfigurationException {

      config.inject(this);
      this.cpa = pCpa;
      this.logger = pLogger;
      shutdownNotifier = pShutdownNotifier;

      if (forcedCoveringClass != null) {
        forcedCovering = forcedCoveringClass.create(config, pLogger, pCpa);
      } else {
        forcedCovering = null;
      }
    }

    @Override
    public RacerCPAAlgorithm newInstance() {
      return new RacerCPAAlgorithm(cpa, logger, shutdownNotifier, forcedCovering, reportFalseAsUnknown);
    }
  }

  public static RacerCPAAlgorithm create(
      ConfigurableProgramAnalysis cpa,
      LogManager logger,
      Configuration config,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {

    return new RacerCPAAlgorithmFactory(cpa, logger, config, pShutdownNotifier).newInstance();
  }

  private final ForcedCovering forcedCovering;

  private final CPAStatistics stats = new CPAStatistics();

  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;

  private final LogManager logger;

  private final ShutdownNotifier shutdownNotifier;

  private final AlgorithmStatus status;

  private RacerCPAAlgorithm(
      ConfigurableProgramAnalysis cpa,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      ForcedCovering pForcedCovering,
      boolean pIsImprecise) {

    transferRelation = cpa.getTransferRelation();
    mergeOperator = cpa.getMergeOperator();
    stopOperator = cpa.getStopOperator();
    precisionAdjustment = cpa.getPrecisionAdjustment();
    this.logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    forcedCovering = pForcedCovering;
    status = AlgorithmStatus.SOUND_AND_PRECISE.withPrecise(!pIsImprecise);
  }

  @Override
  public AlgorithmStatus run(final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    stats.totalTimer.start();
    try {
      return run0(reachedSet);
    } finally {
      stats.stopAllTimers();
      stats.updateReachedSetStatistics(reachedSet.getStatistics());
    }
  }

  private AlgorithmStatus run0(final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    while (reachedSet.hasWaitingState()) {
      shutdownNotifier.shutdownIfNecessary();

      stats.countIterations++;

      // Pick next state using strategy
      // BFS, DFS or top sort according to the configuration
      int size = reachedSet.getWaitlist().size();
      if (size >= stats.maxWaitlistSize) {
        stats.maxWaitlistSize = size;
      }
      stats.countWaitlistSize += size;

      stats.chooseTimer.start();
      final AbstractState state = reachedSet.popFromWaitlist();
      final Precision precision = reachedSet.getPrecision(state);
      stats.chooseTimer.stop();

      logger.log(Level.FINER, "Retrieved state from waitlist");
      try {
        if (handleState(state, precision, reachedSet)) {
          // Prec operator requested break
          return status;
        }
      } catch (CPAException | InterruptedException e) {
        // re-add the old state to the waitlist, there might be unhandled successors left
        // that otherwise would be forgotten (which would be unsound)
        reachedSet.reAddToWaitlist(state);
        throw e;
      }
    }

    return status;
  }

  /**
   * Handle one state from the waitlist, i.e., produce successors etc.
   *
   * @param state The abstract state that was taken out of the waitlist
   * @param precision The precision for this abstract state.
   * @param reachedSet The reached set.
   * @return true if analysis should terminate, false if analysis should continue with next state
   */
  private boolean handleState(
      final AbstractState state, final Precision precision, final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    logger.log(Level.ALL, "Current state is", state, "with precision", precision);

    //System.out.println("State Id: " + ((ARGState)state).getStateId());
    if (forcedCovering != null) {
      stats.forcedCoveringTimer.start();
      try {
        boolean stop = forcedCovering.tryForcedCovering(state, precision, reachedSet);

        if (stop) {
          // TODO: remove state from reached set?
          return false;
        }
      } finally {
        stats.forcedCoveringTimer.stop();
      }
    }

    stats.transferTimer.start();
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(state, precision);
      if (Plan_C_Algorithm.__DEBUG__) {
        System.out.printf("before handle a successor and visited locations num: %d\n", RacerUsageReachedSet.visitedLocations.size());
        System.out.printf("\u001b[32mnewly get %d successors\u001b[0m && waitlist size %d\n", successors.size(), reachedSet.getWaitlist().size());
      }
    } finally {
      stats.transferTimer.stop();
    }
    // TODO When we have a nice way to mark the analysis result as incomplete,
    // we could continue analysis on a CPATransferException with the next state from waitlist.

    int numSuccessors = successors.size();
    logger.log(Level.FINER, "Current state has", numSuccessors, "successors");
    stats.countSuccessors += numSuccessors;
    stats.maxSuccessors = Math.max(numSuccessors, stats.maxSuccessors);

    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext(); ) {
      AbstractState successor = it.next();

      // TODO debug: whenever we processor a successor, we add it locations to visited List first
      addVisitedLocations(successor);

      shutdownNotifier.shutdownIfNecessary();
      logger.log(Level.FINER, "Considering successor of current state");
      logger.log(Level.ALL, "Successor of", state, "\nis", successor);

      stats.precisionTimer.start();
      PrecisionAdjustmentResult precAdjustmentResult;
      try {
        Optional<PrecisionAdjustmentResult> precAdjustmentOptional =
            precisionAdjustment.prec(
                successor, precision, reachedSet, Functions.identity(), successor);
        if (!precAdjustmentOptional.isPresent()) {
          if (Plan_C_Algorithm.__DEBUG__) {
            System.out.println("\u001b[96mprecision prune\u001b[0m");
          }

//           TODO debug
//          ((RacerUsageReachedSet) reachedSet).newSuccessorsInEachIteration.put(successor, null);
//          AbstractStateWithLocations
//              stateWithLocations = extractStateByType(successor, AbstractStateWithLocations.class);
//          stateWithLocations.getLocationNodes().forEach(l -> RacerUsageReachedSet.visitedLocations.add(l));
          if (Plan_C_Algorithm.__DEBUG__) {
            System.out.printf("handled a successor and visited locations num: %d\n", RacerUsageReachedSet.visitedLocations.size());
          }
          continue;
        }
        precAdjustmentResult = precAdjustmentOptional.orElseThrow();
      } finally {
        stats.precisionTimer.stop();
      }

      successor = precAdjustmentResult.abstractState();
      Precision successorPrecision = precAdjustmentResult.precision();
      Action action = precAdjustmentResult.action();

      if (action == Action.BREAK) {
        stats.stopTimer.start();
        boolean stop;
        try {
          stop = stopOperator.stop(successor, reachedSet.getReached(successor), successorPrecision);
        } finally {
          stats.stopTimer.stop();
        }

        if (AbstractStates.isTargetState(successor) && stop) {
          // don't signal BREAK for covered states
          // no need to call merge and stop either, so just ignore this state
          // and handle next successor
          stats.countStop++;
          logger.log(Level.FINER, "Break was signalled but ignored because the state is covered.");
          continue;

        } else {
          stats.countBreak++;
          logger.log(Level.FINER, "Break signalled, CPAAlgorithm will stop.");

          // add the new state
          reachedSet.add(successor, successorPrecision);

          if (it.hasNext()) {
            // re-add the old state to the waitlist, there are unhandled
            // successors left that otherwise would be forgotten
            reachedSet.reAddToWaitlist(state);
            if (Plan_C_Algorithm.__DEBUG__) {
              System.out.println("\u001b[93m successor readd to waitlsit");
            }
          }

          return true;
        }
      }
      assert action == Action.CONTINUE : "Enum Action has unhandled values!";

      Collection<AbstractState> reached = reachedSet.getReached(successor);

      // An optimization, we don't bother merging if we know that the
      // merge operator won't do anything (i.e., it is merge-sep).
      if (mergeOperator != MergeSepOperator.getInstance() && !reached.isEmpty()) {
        stats.mergeTimer.start();
        try {
          List<AbstractState> toRemove = new ArrayList<>();
          List<Pair<AbstractState, Precision>> toAdd = new ArrayList<>();
          try {
            logger.log(
                Level.FINER, "Considering", reached.size(), "states from reached set for merge");
            for (AbstractState reachedState : reached) {
              shutdownNotifier.shutdownIfNecessary();
              AbstractState mergedState =
                  mergeOperator.merge(successor, reachedState, successorPrecision);

              if (!mergedState.equals(reachedState)) {
                logger.log(Level.FINER, "Successor was merged with state from reached set");
                logger.log(
                    Level.ALL, "Merged", successor, "\nand", reachedState, "\n-->", mergedState);
                stats.countMerge++;

                // TODO complete the location cover process
                toRemove.add(reachedState);
                toAdd.add(Pair.of(mergedState, successorPrecision));
              }
            }
          } finally {
            // If we terminate, we should still update the reachedSet if necessary
            // because ARGCPA doesn't like states in toRemove to be in the reachedSet.
            reachedSet.removeAll(toRemove);
            // TODO not sure
//            reachedSet.addAll(toAdd);
            toAdd.forEach(l -> {
              AbstractState s = l.getFirst();
              Precision p = l.getSecond();
              // mergeState's locations should be regards visited?
              ((RacerUsageReachedSet)reachedSet).coveredStatesTable.put(s, p);
              ((RacerUsageReachedSet)reachedSet).addButSkipWaitlist(s, p);
            });
            if (Plan_C_Algorithm.__DEBUG__) {
              System.out.printf("merge add %d\n", toAdd.size());
            }
          }

          if (mergeOperator instanceof ARGMergeJoinCPAEnabledAnalysis) {
            ((ARGMergeJoinCPAEnabledAnalysis) mergeOperator).cleanUp(reachedSet);
          }

        } finally {
          stats.mergeTimer.stop();
        }
      }

      stats.stopTimer.start();
      boolean stop;
      try {
        stop = stopOperator.stop(successor, reached, successorPrecision);
      } finally {
        stats.stopTimer.stop();
      }

      if (stop) {
//        System.out.println("stop!");
        logger.log(Level.FINER, "Successor is covered or unreachable, not adding to waitlist");
        stats.countStop++;
        if (Plan_C_Algorithm.__DEBUG__) {
          System.out.println("successor covered");
        }

      } else {

        // TODO: debug
        if (isLocationCovered(successor)) { // 如果满足Location覆盖
          if (Plan_C_Algorithm.__DEBUG__) {
            System.out.println("\u001b[35msuccessor location covered\u001b[0m");
          }
          /**
           * 将该状态暂时放到被覆盖列表中
           */
          ((RacerUsageReachedSet)reachedSet).coveredStatesTable.put(successor, successorPrecision); //
          ((RacerUsageReachedSet)reachedSet).addButSkipWaitlist(successor, successorPrecision); // 该状态需要添加到reachedSet中，否则会报找不到精度的问题（将状态重新放回Waitlist中时，状态的精度是丢失的）
          // 这里需要重写add方法，不要将状态放回到waitlist中去
          // 如果是因为位置覆盖，则该后继状态应该放到newSuccessorsInEachIteration中去
          ((RacerUsageReachedSet) reachedSet).newSuccessorsInEachIteration.put(successor, successorPrecision);

//          System.out.println("Covered found  &&&  reachedSet size: " + reachedSet.size() + ", state id: " + ((ARGState)successor).getStateId());
        } else {
          logger.log(Level.FINER, "No need to stop, adding successor to waitlist");

          stats.addTimer.start();
          reachedSet.add(successor, successorPrecision);    //reached = reached U {(e_hat, π_hat)}

//          System.out.println("reachedSet size: " + reachedSet.size() + ", state id: " + ((ARGState)successor).getStateId());
          // 将对应的后继添加到newSuccessorsInEachIteration中
          ((RacerUsageReachedSet) reachedSet).newSuccessorsInEachIteration.put(successor, successorPrecision);

          // 将新产生的后继的location放到RacerUsageReachedSet中的visitedLocations中
//            AbstractStateWithLocations
//                stateWithLocations = extractStateByType(successor, AbstractStateWithLocations.class);
//            stateWithLocations.getLocationNodes().forEach(l -> RacerUsageReachedSet.visitedLocations.add(l));

          if (Plan_C_Algorithm.__DEBUG__) {
            System.out.println("\u001b[33msuccessor added to reachSet\u001b[0m");
          }
          stats.addTimer.stop();
        }
      }
      if (Plan_C_Algorithm.__DEBUG__) {
        System.out.printf("handled one successor and visited locations num: %d\n", RacerUsageReachedSet.visitedLocations.size());
      }
    }

    return false;
  }

  // add visited locations
  public void addVisitedLocations(AbstractState pState) {
     /* 将新产生的后继的location放到RacerUsageReachedSet中的visitedLocations中 */
    AbstractStateWithLocations stateWithLocations = extractStateByType(pState, AbstractStateWithLocations.class);
    stateWithLocations.getLocationNodes().forEach(l -> RacerUsageReachedSet.visitedLocations.add(l));
  }

  // check whether location covered
  public boolean isLocationCovered(AbstractState pState) {

    RacerThreadingState suc = extractStateByType(pState, RacerThreadingState.class);
    Iterable<CFANode> locs = suc.getLocationNodes();
//        System.out.printf("visited locations num: %d\n", RacerUsageReachedSet.visitedLocations.size());
    Iterator<CFANode> ite = locs.iterator();
    suc.locationCovered = true;
    while (ite.hasNext()) {
      if (!RacerUsageReachedSet.visitedLocations.contains(ite.next())) {
        suc.locationCovered = false;
      }
    }
    return suc.locationCovered;
  }
  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (forcedCovering instanceof StatisticsProvider) {
      ((StatisticsProvider) forcedCovering).collectStatistics(pStatsCollection);
    }
    pStatsCollection.add(stats);
  }
}