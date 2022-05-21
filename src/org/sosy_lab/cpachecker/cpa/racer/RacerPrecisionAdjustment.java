// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import java.util.Optional;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.defaults.RacerAbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSetView;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RacerPrecisionAdjustment implements PrecisionAdjustment {

  private final PrecisionAdjustment wrappedPrecisionAdjustment;
  private final RacerCPAStatistics stats;

  public RacerPrecisionAdjustment(PrecisionAdjustment pWrappedPrecisionAdjustment, RacerCPAStatistics pStats) {
    wrappedPrecisionAdjustment = pWrappedPrecisionAdjustment;
    stats = pStats;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      // TODO not accomplished
      AbstractState pElement,
      Precision oldPrecision,
      UnmodifiableReachedSet pReachedSet,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState fullState) throws CPAException, InterruptedException {

    stats.precTimer.start();
    Preconditions.checkArgument(pElement instanceof RacerState);
    RacerState element = (RacerState) pElement;

    UnmodifiableReachedSet elements = new UnmodifiableReachedSetView( pReachedSet, RacerAbstractSingleWrapperState.getUnwrapFunction(), Functions.identity());
    AbstractState oldElement = element.getWrappedState();

    Optional<PrecisionAdjustmentResult> optionalUnwrappedResult =
        wrappedPrecisionAdjustment.prec(
            oldElement,
            oldPrecision,
            elements,
            Functions.compose(RacerAbstractSingleWrapperState.getUnwrapFunction(), pStateProjection),
            fullState);

    if (!optionalUnwrappedResult.isPresent()) {
      stats.precTimer.stop();
      return Optional.empty();
    }

    PrecisionAdjustmentResult unwrappedResult = optionalUnwrappedResult.orElseThrow();

    AbstractState newElement = unwrappedResult.abstractState();
    Precision newPrecision = unwrappedResult.precision();
    Action action = unwrappedResult.action();

    if ((oldElement == newElement) && (oldPrecision == newPrecision)) {
      // nothing has changed
      stats.precTimer.stop();
      return Optional.of(PrecisionAdjustmentResult.create(pElement, oldPrecision, action));
    }

    RacerState resultElement = element.copy(newElement);

    stats.precTimer.stop();
    return Optional.of(PrecisionAdjustmentResult.create(resultElement, newPrecision, action));
  }
}
