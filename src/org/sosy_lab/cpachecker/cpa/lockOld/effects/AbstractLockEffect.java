// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lockOld.effects;

import org.sosy_lab.cpachecker.cpa.lockOld.AbstractLockStateBuilder;
import org.sosy_lab.cpachecker.cpa.lockOld.LockIdentifier;


public interface AbstractLockEffect {
  public void effect(AbstractLockStateBuilder builder);

  public AbstractLockEffect applyToTarget(LockIdentifier pId);
}
