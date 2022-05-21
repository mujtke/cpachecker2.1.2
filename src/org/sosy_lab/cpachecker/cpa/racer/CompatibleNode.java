// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import org.sosy_lab.cpachecker.cpa.racer.storage.Delta;
import org.sosy_lab.cpachecker.cpa.racer.storage.GenericDelta;

public interface CompatibleNode extends CompatibleState {

  public boolean cover(CompatibleNode node);

  public default boolean hasEmptyLockSet() {
    return true;
  }

  @Override
  public default CompatibleNode getCompatibleNode() {
    return this;
  }

  public default Delta<CompatibleNode> getDeltaBetween(CompatibleNode pOther) {
    return GenericDelta.getInstance();
  }
}
