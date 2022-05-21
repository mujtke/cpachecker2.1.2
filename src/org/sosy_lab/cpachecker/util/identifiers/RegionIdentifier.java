// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.identifiers;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

public class RegionIdentifier extends SingleIdentifier implements GeneralIdentifier {

  public RegionIdentifier(String pNm, CType pTp) {
    super(pNm, pTp, 1);
  }

  @Override
  public boolean isGlobal() {
    return true;
  }

  @Override
  public AbstractIdentifier cloneWithDereference(int pDereference) {
    return this;
  }

  @Override
  public Collection<AbstractIdentifier> getComposedIdentifiers() {
    return ImmutableSet.of();
  }

  @Override
  public String toLog() {
    return "r;" + name + ";" + dereference;
  }

  @Override
  public GeneralIdentifier getGeneralId() {
    return this;
  }

  @Override
  public int compareTo(AbstractIdentifier pO) {
    if (pO instanceof GlobalVariableIdentifier
        || pO instanceof LocalVariableIdentifier
        || pO instanceof StructureIdentifier) {
      return -1;
    } else if (pO instanceof RegionIdentifier) {
      return super.compareTo(pO);
    } else {
      return 1;
    }
  }
}
