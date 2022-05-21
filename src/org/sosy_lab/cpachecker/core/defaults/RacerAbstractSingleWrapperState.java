// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.defaults;

import com.google.common.base.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class RacerAbstractSingleWrapperState extends AbstractSingleWrapperState {

  protected RacerAbstractSingleWrapperState(@Nullable AbstractState pWrappedState) {
    super(pWrappedState);
  }

  public static Function<AbstractState, AbstractState> getUnwrapFunction() {
    return pArg0 -> ((AbstractSingleWrapperState)pArg0).getWrappedState();
  }
}
