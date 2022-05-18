// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

#include <assert.h>

int x = 5;

void check_global() {
	assert(x == 5);
}

void main() {
	int x;
	x = 3;
	assert(x == 3);
	check_global();
}
