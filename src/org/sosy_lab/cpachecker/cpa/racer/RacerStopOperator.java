// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RacerStopOperator implements StopOperator {
  private final StopOperator wrappedStop;
  private final RacerCPAStatistics stats;

  public RacerStopOperator(StopOperator pWrappedStop, RacerCPAStatistics pStatistics) {
    wrappedStop = pWrappedStop;
    stats = pStatistics;
  }

  @Override
  public boolean stop(
      // TODO unusable
      AbstractState pState, Collection<AbstractState> pReached, Precision pPrecision)
      throws CPAException, InterruptedException {

    RacerState racerState = (RacerState) pState;

    stats.stopTimer.start();
    for (AbstractState reached : pReached) {
      RacerState reachedRacerState = (RacerState) reached;
      stats.racerStopTimer.start();
      boolean result = racerState.isLessOrEqual(reachedRacerState);
      stats.racerStopTimer.stop();
      if (!result) {
        continue;
      }
      stats.wrappedStopTimer.start();
      result =
          wrappedStop.stop(
              racerState.getWrappedState(),
              Collections.singleton(reachedRacerState.getWrappedState()),
              pPrecision);
      stats.wrappedStopTimer.stop();
      if (result) {
        stats.stopTimer.stop();
        return true;
      }
    }
    stats.stopTimer.stop();
    return false;
  }
}
