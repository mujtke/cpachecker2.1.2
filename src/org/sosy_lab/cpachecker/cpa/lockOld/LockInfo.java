// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lockOld;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.sosy_lab.cpachecker.cpa.lockOld.LockIdentifier;
import org.sosy_lab.cpachecker.cpa.lockOld.effects.AbstractLockEffect;
import org.sosy_lab.cpachecker.util.Pair;

public class LockInfo {

  private final ImmutableMap<String, Pair<AbstractLockEffect, LockIdParametrized>> functionEffectDescription;
  private final ImmutableMap<String, LockIdentifier> variableEffectDescription;
  private final ImmutableMap<String, Integer> maxLevel;

  public LockInfo(
      Map<String, Pair<AbstractLockEffect, LockIdParametrized>> functionEffects,
      Map<String, LockIdentifier> varEffects,
      Map<String, Integer> max) {
    functionEffectDescription = ImmutableMap.copyOf(functionEffects);
    variableEffectDescription = ImmutableMap.copyOf(varEffects);
    maxLevel = ImmutableMap.copyOf(max);
  }

  public ImmutableMap<String, Pair<AbstractLockEffect, LockIdParametrized>>
      getFunctionEffectDescription() {
    return functionEffectDescription;
  }

  public ImmutableMap<String, LockIdentifier> getVariableEffectDescription() {
    return variableEffectDescription;
  }

  public int getMaxLevel(String lockName) {
    // The max level must be in map (do not use default)
    return maxLevel.get(lockName);
  }

}
