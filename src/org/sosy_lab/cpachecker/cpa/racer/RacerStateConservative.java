// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public class RacerStateConservative extends RacerState {
  private static final long serialVersionUID = -8505134232125928168L;

  private RacerStateConservative(
      final AbstractState pWrappedElement,
      final ImmutableMap<AbstractIdentifier, AbstractIdentifier> pVarBind,
      final RacerState.StateStatistics pStats) {
    super(pWrappedElement, pVarBind, pStats);
  }

  public static RacerStateConservative createInitialState(final AbstractState pWrappedElement) {
    return new RacerStateConservative(
        pWrappedElement,
        ImmutableMap.of(),
        new RacerState.StateStatistics());
  }

  @Override
  public RacerStateConservative copy(final AbstractState pWrappedState) {
    return new RacerStateConservative(pWrappedState, this.variableBindingRelation, this.stats);
  }

  @Override
  protected RacerStateConservative createState(
      final AbstractState pWrappedState,
      final ImmutableMap<AbstractIdentifier, AbstractIdentifier> pVarBind,
      final RacerState.StateStatistics pStats) {
    return new RacerStateConservative(pWrappedState, pVarBind, pStats);
  }

  @Override
  public void filterAliases(AbstractIdentifier pIdentifier, Collection<AbstractIdentifier> pSet) {
    return;
  }
}
