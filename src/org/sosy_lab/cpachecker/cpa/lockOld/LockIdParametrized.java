// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.lockOld;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cpa.lockOld.LockIdentifier;

public class LockIdParametrized {

  private int p;
  private String lockName;

  public LockIdParametrized(String pName, int num) {
    p = num;
    lockName = pName;
  }

  public LockIdentifier apply(List<CExpression> params) {
    if (p == 0) {
      return LockIdentifier.of(lockName);
    } else {
      return LockIdentifier.of(lockName, params.get(p - 1).toASTString());
    }
  }
}
