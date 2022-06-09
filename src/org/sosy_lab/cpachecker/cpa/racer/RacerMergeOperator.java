// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RacerMergeOperator implements MergeOperator {
  private final MergeOperator wrappedMerge;
  private final RacerCPAStatistics stats;
  private final boolean argsBind;

  public RacerMergeOperator(MergeOperator pWrappedMerge, RacerCPAStatistics pStats, boolean pArgsBind) {
    wrappedMerge = pWrappedMerge;
    stats = pStats;
    argsBind = pArgsBind;
  }

  @Override
  public AbstractState merge(
      // TODO unusable
      AbstractState pState1, AbstractState pState2, Precision pPrecision)
      throws CPAException, InterruptedException {

    if (argsBind) {
      return pState2;
    } else {
      stats.mergeTimer.start();
      RacerState uState1 = (RacerState) pState1;
      RacerState uState2 = (RacerState) pState2;

      AbstractState wrappedState1 = uState1.getWrappedState();
      AbstractState wrappedState2 = uState2.getWrappedState();

      AbstractState mergedState = wrappedMerge.merge(wrappedState1, wrappedState2, pPrecision);

      RacerState result;

      /* TODO merge just depend on BDD region */
      if (mergedState.equals(wrappedState2)) {
        stats.mergeTimer.stop();
        return pState2;
      }
      if (uState1.isLessOrEqual(uState2)) {
        result = uState2.copy(mergedState);
      } else if (uState2.isLessOrEqual(uState1)) {
        result = uState1.copy(mergedState);
      } else {
        result = uState1.copy(mergedState);
        result.join(uState2);
      }
      stats.mergeTimer.stop();
      return result;

      // RacerState a <= b if all a's variableBindingRelation are in b
//      if (uState1.isLessOrEqual(uState2)) {
//        result = uState2.copy(mergedState);
//      } else if (uState2.isLessOrEqual(uState1)) {
//        result = uState1.copy(mergedState);
//      } else {
//        result = uState1.copy(mergedState);
//        result.join(uState2);
//      }
//
//      if (mergedState.equals(wrappedState2) && result.equals(uState2)) {
//        stats.mergeTimer.stop();
//        return pState2;
//      } else {
//        stats.mergeTimer.stop();
//        return result;
//      }
    }
  }
}
