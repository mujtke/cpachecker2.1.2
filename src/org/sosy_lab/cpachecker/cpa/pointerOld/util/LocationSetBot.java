// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.pointerOld.util;

import org.sosy_lab.cpachecker.cpa.pointerOld.util.ExplicitLocationSet;
import org.sosy_lab.cpachecker.cpa.pointerOld.util.LocationSet;
import org.sosy_lab.cpachecker.util.states.RCUMemoryLocation;



public enum LocationSetBot implements LocationSet {

  INSTANCE;

  @Override
  public boolean mayPointTo(RCUMemoryLocation pTarget) {
    return false;
  }

  @Override
  public LocationSet addElement(RCUMemoryLocation pTarget) {
    return ExplicitLocationSet.from(pTarget);
  }

  @Override
  public LocationSet removeElement(RCUMemoryLocation pTarget) {
    return this;
  }

  @Override
  public LocationSet addElements(Iterable<RCUMemoryLocation> pTargets) {
    return ExplicitLocationSet.from(pTargets);
  }

  @Override
  public boolean isBot() {
    return true;
  }

  @Override
  public boolean isTop() {
    return false;
  }

  @Override
  public LocationSet addElements(LocationSet pElements) {
    return pElements;
  }

  @Override
  public boolean containsAll(LocationSet pElements) {
    return pElements.isBot();
  }

  @Override
  public String toString() {
    return Character.toString('\u22A5');

  }

}
