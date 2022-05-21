// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WrapperPrecision;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;

public class RacerPrecision implements WrapperPrecision, AdjustablePrecision {
  private final Map<CFANode, Map<GeneralIdentifier, DataType>> localStatistics;
  private final Precision wrappedPrecision;

  private RacerPrecision(
      Precision pWrappedPrecision, Map<CFANode, Map<GeneralIdentifier, DataType>> pMap) {
    localStatistics = pMap;
    wrappedPrecision = pWrappedPrecision;
  }

  static RacerPrecision create(
      Precision pWrappedPrecision, Map<CFANode, Map<GeneralIdentifier, DataType>> pMap) {
    return new RacerPrecision(pWrappedPrecision, ImmutableMap.copyOf(pMap));
  }

  @Override
  public AdjustablePrecision add(AdjustablePrecision otherPrecision) {
    return null;
  }

  @Override
  public AdjustablePrecision subtract(AdjustablePrecision otherPrecision) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public <T extends Precision> @Nullable T retrieveWrappedPrecision(
      Class<T> type) {
    return null;
  }

  @Override
  public @Nullable Precision replaceWrappedPrecision(
      Precision newPrecision, Predicate<? super Precision> replaceType) {
    return null;
  }

  @Override
  public Iterable<Precision> getWrappedPrecisions() {
    return null;
  }
}
