// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer.storage;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.racer.RacerUsageCPA;
import org.sosy_lab.cpachecker.cpa.racer.RacerUsageProcessor;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;

public class RacerUsageExtractor {

  private final LogManager logger;
  private final RacerUsageContainer container;
  private final RacerUsageProcessor usageProcessor;
  private UsageDelta currentDelta;
  private final Map<AbstractState, List<UsageInfo>> stateToUsage;
  private final List<AbstractState> expandedStack;
  public boolean haveUsagesExtracted;         // 增加一个字段用来判断是否在某次迭代中产生了不安全
  private final ShutdownNotifier notifier;

  private final ThreadSafeTimerContainer usageExpandingTimer =
      new ThreadSafeTimerContainer("Time for usage expanding");
  private final ThreadSafeTimerContainer usageProcessingTimer =
      new ThreadSafeTimerContainer("Time for usage calculation");
  private final ThreadSafeTimerContainer addingToContainerTimer =
      new ThreadSafeTimerContainer("Time for adding to container");

  private final StatTimer totalTimer = new StatTimer("Time for extracting usages");
  private final ThreadSafeTimerContainer.TimerWrapper addingTimer;
  private final ThreadSafeTimerContainer.TimerWrapper expandingTimer;
  private final ThreadSafeTimerContainer.TimerWrapper processingTimer;

  public RacerUsageExtractor(ConfigurableProgramAnalysis pCpa, LogManager pLogger, RacerUsageContainer pContainer, RacerUsageConfiguration pUsageConfiguration) {
    logger = pLogger;
    container = pContainer;
    RacerUsageCPA racerUsageCPA = CPAs.retrieveCPA(pCpa, RacerUsageCPA.class);
    usageProcessor = racerUsageCPA.getUsageProcessor();
    stateToUsage = new HashMap<>();
    expandedStack = ImmutableList.of();
    notifier = racerUsageCPA.getNotifier();
    addingTimer = addingToContainerTimer.getNewTimer();
    expandingTimer = usageExpandingTimer.getNewTimer();
    processingTimer = usageProcessingTimer.getNewTimer();
  }

  /**
   * 对提取Usage进行修改，只提取每轮更新中得到的新后继的usages
   * @param firstState
   */
  public void extractUsages(LinkedHashMap<AbstractState, Precision> newSucsInEachIteration) {
    totalTimer.start();
    Multimap<AbstractState, UsageDelta> processedSets = ArrayListMultimap.create();

    Set<Entry<AbstractState, Precision>> entrySet = newSucsInEachIteration.entrySet();
    Iterator<Entry<AbstractState, Precision>> it = entrySet.iterator();
    AbstractState firstState = it.next().getKey();  //取出第一个状态

    UsageDelta emptyDelta = UsageDelta.constructDeltaBetween(firstState, firstState);
    currentDelta = emptyDelta;
    processedSets.put(firstState, emptyDelta);
    usageProcessor.updateRedundantUnsafes(container.getNotInterestingUnsafes());    // 不感兴趣的是那些已经细化和呈现假阳的id，updateRedundantUnsafes(...)将不感兴趣的那些id设为redundant

    Deque<AbstractState> stateWaitlist = new ArrayDeque<>();
    stateWaitlist.add(firstState);      //firstStat为可达集中的第一个状态

    // Waitlist to be sure in order (not start from the middle point)
    while (!stateWaitlist.isEmpty()) {
      ARGState argState = (ARGState) stateWaitlist.poll();
      // TODO: debug
      //System.out.println("extract usage: state " + argState.getStateId());
      // TODO stateToUsage似乎可以不用
//      if (stateToUsage.containsKey(argState)) {       // 该状态已经被分析过了
//        continue;
//      }

      List<UsageInfo> expandedUsages = expandUsagesAndAdd(argState);

      addingTimer.start();
      for (UsageInfo usage : expandedUsages) {
        container.add(usage);
        haveUsagesExtracted = true;
      }
      addingTimer.stop();

      if (it.hasNext())
        stateWaitlist.add(it.next().getKey());     // 将newSucsInEachIteratiron中的下一个状态放到waitlist中
    }

    totalTimer.stop();
  }

  private List<UsageInfo> expandUsagesAndAdd(ARGState state) {

    List<UsageInfo> expandedUsages = new ArrayList<>();

    // 去掉了stateToUsage
//    for (ARGState covered : state.getCoveredByThis()) {
//      expandedUsages.addAll(stateToUsage.getOrDefault(covered, ImmutableList.of()));  // 如果stateToUsage的key包含covered，则返回stateToUsage.get(covered)
//      // 否则返回空的list
//    }
//    for (ARGState parent : state.getParents()) {
//      expandedUsages.addAll(stateToUsage.getOrDefault(parent, ImmutableList.of()));
//    }

    processingTimer.start();
//        List<UsageInfo> usages = usageProcessor.getUsagesForState(state);    // 利用子节点来获取usage
    List<UsageInfo> usages = usageProcessor.getUsagesForStateByParentState(state);      // 利用父节点来获取usage
    processingTimer.stop();

    expandingTimer.start();
    for (UsageInfo usage : usages) {
      UsageInfo expanded = usage.expand(currentDelta, expandedStack);
      if (expanded.isRelevant()) {    // 扩展后的usage可能是Irrelevant
        expandedUsages.add(expanded);
      }
    }
    expandingTimer.stop();

    return expandedUsages;
  }

  public void printSatistics(StatisticsWriter pWriter) {
    StatisticsWriter writer =
        pWriter.spacer().put(totalTimer).beginLevel().put(usageProcessingTimer).beginLevel();
    usageProcessor.printStatistics(writer);
    writer.endLevel()
        .put(addingToContainerTimer)
        .put(usageExpandingTimer)
        .endLevel();
  }
}
