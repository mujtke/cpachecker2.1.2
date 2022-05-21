// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import java.util.Collection;

import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public interface AliasInfoProvider {
  Collection<AbstractIdentifier> getAllPossibleAliases(AbstractIdentifier id);

  @SuppressWarnings("unused")
  default void filterAliases(AbstractIdentifier pIdentifier, Collection<AbstractIdentifier> pSet) {
  }
}
