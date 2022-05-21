// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer.storage;

import com.google.common.collect.ForwardingSet;
import com.google.common.collect.Iterables;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo;
import org.sosy_lab.cpachecker.cpa.racer.RacerState;

public class UsageInfoSet extends ForwardingSet<UsageInfo> {
  private final Set<UsageInfo> usageSet;

  public UsageInfoSet() {
    // TODO debug 0516
    //usageSet = new ConcurrentSkipListSet<>();
    usageSet = new TreeSet<>();
  }

  private UsageInfoSet(Set<UsageInfo> pSet) {
    // TODO debug 0516
    //usageSet = new ConcurrentSkipListSet<>(pSet);
    usageSet = new TreeSet<>(pSet);
  }

  public boolean remove(RacerState pUstate) {
    Iterator<UsageInfo> iterator = usageSet.iterator();
    boolean changed = false;
    while (iterator.hasNext()) {
      UsageInfo uinfo = iterator.next();
      AbstractState keyState = uinfo.getKeyState();
      assert (keyState != null);
      if (RacerState.get(keyState).equals(pUstate)) {
        iterator.remove();
        changed = true;
      }
    }
    return changed;
  }

  public UsageInfo getOneExample() {
    return Iterables.get(usageSet, 0);
  }

  public UsageInfoSet copy() {
    // For avoiding concurrent modification in refinement
    return new UsageInfoSet(usageSet);
  }

  @Override
  protected Set<UsageInfo> delegate() {
    return usageSet;
  }
}
