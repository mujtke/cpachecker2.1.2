// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racerThreading;

import java.util.Collection;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class RacerThreadingStopOperator implements StopOperator {

    /**
     * 根据位置覆盖来判断是否需要暂停该位置处的探索
     * @param state
     * @param reached
     * @param precision
     * @return
     * @throws CPAException
     * @throws InterruptedException
     */
    @Override
    public boolean stop(AbstractState state, Collection<AbstractState> reached, Precision precision) throws CPAException, InterruptedException {

        // TODO: debug 0607
//        if (true) return true;
        assert state instanceof RacerThreadingState;

        boolean stop = true;
//        boolean locationStop = true;
        RacerThreadingState pState = (RacerThreadingState) state;
//        Iterable<CFANode> Locs = pState.getLocationNodes();
//        Iterator<CFANode> it = Locs.iterator();
//        while (it.hasNext()) {
//            CFANode p = it.next();
//            if (!Plan_C_UsageReachedSet.visitedLocations.contains(p)) {
//                locationStop = false;
//                break;
//            }
//        }
//        if (locationStop) {   // 如果Location覆盖，则将该usageState的locationCovered设置为true
//            pState.locationCovered = true;
//        }
        // TODO: cover比较的是当前状态和reached中的状态
        for (AbstractState other : reached) {
            RacerThreadingState o = (RacerThreadingState) other;
            if (!pState.currentThread.equals(o.currentThread) || !pState.threadSet.equals(o.threadSet)) {
                stop = false;
            }
        }

        return stop;
    }
}
