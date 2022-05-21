// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.pointerOld.util;

import org.sosy_lab.cpachecker.util.states.RCUMemoryLocation;


public interface LocationSet {

  boolean mayPointTo(RCUMemoryLocation pLocation);

  LocationSet addElement(RCUMemoryLocation pLocation);

  LocationSet removeElement(RCUMemoryLocation pLocation);

  LocationSet addElements(Iterable<RCUMemoryLocation> pLocations);

  LocationSet addElements(LocationSet pLocations);

  boolean isBot();

  boolean isTop();

  boolean containsAll(LocationSet pLocations);

}
