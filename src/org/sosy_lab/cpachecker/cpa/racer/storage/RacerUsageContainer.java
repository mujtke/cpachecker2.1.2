// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer.storage;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.racer.RacerState;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo;
import org.sosy_lab.cpachecker.cpa.racer.storage.AbstractUsagePointSet;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class RacerUsageContainer {
  private final NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> unrefinedIds;
  private final NavigableMap<SingleIdentifier, RefinedUsagePointSet> refinedIds;
  private final NavigableMap<SingleIdentifier, RefinedUsagePointSet> failedIds;
  private final NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> haveUnsafesIds;

  private Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> stableUnsafes = new TreeMap<>();

  private final RacerUnsafeDetector detector;
  private Set<SingleIdentifier> falseUnsafes;
  private final Set<SingleIdentifier> processedUnsafes = new HashSet<>();
  private Set<SingleIdentifier> initialUnsafes;

  private boolean oneTotalIteration = false;

  // Only for statistics
  private final LogManager logger;
  private final RacerUsageConfiguration config;

  private final StatTimer resetTimer = new StatTimer("Time for reseting unsafes");
  private final StatTimer copyTimer = new StatTimer("Time for filling global container");
  private final StatTimer emptyEffectsTimer = new StatTimer("Time for coping usages");private int initialUsages;
  private final StatTimer unsafeDetectionTimer = new StatTimer("Time for unsafe detection");
  private final StatCounter sharedVariables =
      new StatCounter("Number of detected shared variables");

  public NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> getUnrefinedIds() {
    return unrefinedIds;
  }

  private RacerUsageContainer(
      NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> pUnrefinedIds,
      NavigableMap<SingleIdentifier, RefinedUsagePointSet> pRefinedIds,
      NavigableMap<SingleIdentifier, RefinedUsagePointSet> pFailedIds,
      NavigableMap<SingleIdentifier, UnrefinedUsagePointSet> pHaveUnsafesIds,
      RacerUnsafeDetector pDetector,
      Set<SingleIdentifier> pFalseUnsafes,
      LogManager pLogger,
      RacerUsageConfiguration pConfig) {
     unrefinedIds = pUnrefinedIds;
     refinedIds = pRefinedIds;
     failedIds = pFailedIds;
     haveUnsafesIds = pUnrefinedIds;
     detector = pDetector;
     falseUnsafes = pFalseUnsafes;
     logger = pLogger;
     config = pConfig;
  }

  public RacerUsageContainer(RacerUsageConfiguration pConfig, LogManager l, RacerUnsafeDetector pDetector) {
    this(
        new TreeMap<SingleIdentifier, UnrefinedUsagePointSet>(),
        new TreeMap<SingleIdentifier, RefinedUsagePointSet>(),
        new TreeMap<SingleIdentifier, RefinedUsagePointSet>(),
        new TreeMap<SingleIdentifier, UnrefinedUsagePointSet>(),
        pDetector,
        new TreeSet<SingleIdentifier>(),
        l,
        pConfig);
  }

  public RacerUsageContainer(RacerUsageConfiguration pConfig, LogManager pLogger) {
    unrefinedIds = new TreeMap<>();
    refinedIds = new TreeMap<>();
    haveUnsafesIds = new TreeMap<>();
    failedIds = new TreeMap<>();
    falseUnsafes = new TreeSet<>();
    detector = new RacerUnsafeDetector(pConfig);
    logger = pLogger;
    config = pConfig;
  }

  public void removeState(final RacerState pUstate) {
    unrefinedIds.forEach((id, uset) -> uset.remove(pUstate));
    logger.log(
        Level.ALL,
        "All unsafes related to key state " + pUstate + "were removed from reached set");
  }

  public void resetUnrefinedUnsafes() {
    haveUnsafesIds.clear();
  }

  public Set<SingleIdentifier> getNotInterestingUnsafes() {
    return new TreeSet<>(Sets.union(falseUnsafes, refinedIds.keySet()));
  }

  /**
   * 向container中添加usageInfo
   * container在添加usageInfo时，会将identifier单独提取出来
   * 如果该id尚未被添加到unrefinedIds中，
   * 则将UsageInfo添加到该id所对应的UnrefinedUsagePointSet集合中
   * UnrefinedUsagePointSet中包含了对应id的topUsages和UsageInfoSets
   * UsageInfoSets： key为UsagePoint，value为UsageInfo的集合
   * @param pUsage
   */
  public void add(UsageInfo pUsage) {
    SingleIdentifier id = pUsage.getId();
    if (id instanceof StructureIdentifier) {
      id = ((StructureIdentifier) id).toStructureFieldIdentifier();
    }

    UnrefinedUsagePointSet uset;

    // searchingInCachesTimer.start();
    if (oneTotalIteration && !unrefinedIds.containsKey(id)) {
      // searchingInCachesTimer.stop();
      return;
    }

    if (!unrefinedIds.containsKey(id)) {
      uset = new UnrefinedUsagePointSet();
      // It is possible, that someone place the set after check
      UnrefinedUsagePointSet present = unrefinedIds.putIfAbsent(id, uset);  // 如果id之前就对应了一个UnrefinedUsagePointSet集合，则present = null
      if (present != null) {                                                // 否则present = 之前对应的UnrefinedUsagePointSet集合
        uset = present;                                                     // 在此之前，uset为空
      }
    } else {
      uset = unrefinedIds.get(id);
    }
    // searchingInCachesTimer.stop();

    // addingToSetTimer.start();
    uset.add(pUsage);         // 将UsageInfo放到对应id的UnrefinedUsagePointSet集合中，不过会pUsage是否被覆盖进行判断
    // addingToSetTimer.stop();
  }


  // TODO may modify the way to detect race
  public boolean hasUnsafesForUsageContainer() {
    haveUnsafesIds.clear();     //防止空指针异常
    findUnsafesIfExist();
    stableUnsafes.clear();
//    addUnsafesFrom(refinedIds);
    addUnsafesFrom(haveUnsafesIds);    // 将存在存在Unsafes的ids放到stableUnsafes中

    return !stableUnsafes.isEmpty();
  }

  private void findUnsafesIfExist() {
    unsafeDetectionTimer.start();

    Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>>
        iterator =
        unrefinedIds.entrySet().iterator();             //
//    falseUnsafes = new TreeSet<>(unrefinedIds.keySet());    //先假设所有的id都为不安全
    while (iterator.hasNext()) {
      Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
      UnrefinedUsagePointSet tmpList = entry.getValue();    //包含对特定id的topUsage和UsageInfoSets
      if (detector.isUnsafe(tmpList)) {                     //若对某个id计算出存在不安全
        if (!oneTotalIteration) {
          initialUsages += tmpList.size();
        }
//        falseUnsafes.remove(entry.getKey());                //则将对应的id从falseUnsafes中移除
        haveUnsafesIds.put(entry.getKey(), entry.getValue()); //将该id添加到haveUnsafesIds中
      } else {                                              //对当前id的计算没有发现不安全
        if (!oneTotalIteration) {
          sharedVariables.inc();
        }
        //iterator.remove();
      }
    }

    unsafeDetectionTimer.stop();
  }

  private void addUnsafesFrom(
      NavigableMap<SingleIdentifier, ? extends AbstractUsagePointSet> storage) {

    for (Entry<SingleIdentifier, ? extends AbstractUsagePointSet> entry : storage.entrySet()) {
      Pair<UsageInfo, UsageInfo> tmpPair = detector.getUnsafePair(entry.getValue());
      stableUnsafes.put(entry.getKey(), tmpPair);
      // TODO add targetInfo to keyState for Usage
      AbstractState s1 = tmpPair.getFirst().getKeyState();
      AbstractState s2 = tmpPair.getSecond().getKeyState();
      RacerState r1 = AbstractStates.extractStateByType(s1, RacerState.class);
      RacerState r2 = AbstractStates.extractStateByType(s2, RacerState.class);
      r1.setRaceKeyState(true);
      r2.setRaceKeyState(true);
    }
  }

  public Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> getStableUnsafes() {
    return stableUnsafes;
  }

  public Set<SingleIdentifier> getFalseUnsafes() {
    return falseUnsafes;
  }
  public void printUsagesStatistics(StatisticsWriter out) {
    int unsafeSize = getTotalUnsafeSize();
    StatInt topUsagePoints = new StatInt(StatKind.SUM, "Total amount of unrefined usage points");
    StatInt unrefinedUsages = new StatInt(StatKind.SUM, "Total amount of unrefined usages");
    StatInt refinedUsages = new StatInt(StatKind.SUM, "Total amount of refined usages");
    StatCounter failedUsages = new StatCounter("Total amount of failed usages");

    final int generalUnrefinedSize = unrefinedIds.keySet().size();
    for (UnrefinedUsagePointSet uset : unrefinedIds.values()) {
      unrefinedUsages.setNextValue(uset.size());
      topUsagePoints.setNextValue(uset.getNumberOfTopUsagePoints());
    }

    int generalRefinedSize = 0;
    int generalFailedSize = 0;

    for (RefinedUsagePointSet uset : refinedIds.values()) {
      Pair<UsageInfo, UsageInfo> pair = uset.getUnsafePair();
      UsageInfo firstUsage = pair.getFirst();
      UsageInfo secondUsage = pair.getSecond();

      if (firstUsage.isLooped()) {
        failedUsages.inc();
        generalFailedSize++;
      }
      if (secondUsage.isLooped() && !firstUsage.equals(secondUsage)) {
        failedUsages.inc();
      }
      if (!firstUsage.isLooped() && !secondUsage.isLooped()) {
        generalRefinedSize++;
        refinedUsages.setNextValue(uset.size());
      }
    }

    out.spacer()
        .put(sharedVariables)
        .put("Total amount of unsafes", unsafeSize)
        .put("Initial amount of unsafes (before refinement)", unsafeSize + falseUnsafes.size())
        .put("Initial amount of usages (before refinement)", initialUsages)
        .put("Initial amount of refined false unsafes", falseUnsafes.size())
        .put("Total amount of unrefined unsafes", generalUnrefinedSize)
        .put(topUsagePoints)
        .put(unrefinedUsages)
        .put("Total amount of refined unsafes", generalRefinedSize)
        .put(refinedUsages)
        .put("Total amount of failed unsafes", generalFailedSize)
        .put(failedUsages)
        .put(resetTimer)
        .put(unsafeDetectionTimer);
  }

  // TODO 以下内容在Plan_C中未使用
  public int getTotalUnsafeSize() {
    calculateUnsafesIfNecessary();
    return unrefinedIds.size() + refinedIds.size();
  }

  public Iterator<SingleIdentifier> getUnsafeIterator() {
    calculateUnsafesIfNecessary();
    Set<SingleIdentifier> result = new TreeSet<>(refinedIds.keySet());
    result.addAll(unrefinedIds.keySet());
    return result.iterator();
  }

  private void calculateUnsafesIfNecessary() {
    unsafeDetectionTimer.start();

    Iterator<Entry<SingleIdentifier, UnrefinedUsagePointSet>> iterator =
        unrefinedIds.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<SingleIdentifier, UnrefinedUsagePointSet> entry = iterator.next();
      UnrefinedUsagePointSet tmpList = entry.getValue();    //包含对特定id的topUsage和UsageInfoSets
      if (detector.isUnsafe(tmpList)) {
        if (!oneTotalIteration) {
          initialUsages += tmpList.size();
        }
      } else {
        if (!oneTotalIteration) {
          sharedVariables.inc();
        }
        iterator.remove();    //如果没有构成Unsafe，则将unrefinedIds中对应的条目删除
      }
    }
    //到这里，unrefinedIds中的条目数量会减少很多，应该是那些不存在Unsafe的被删除了
    if (!oneTotalIteration) {
      initialUnsafes = new TreeSet<>(unrefinedIds.keySet());   //跟oneTotalIteration的取值有关，会在其取值为false时将unrefinedIds中存在着Unsafe情况的所有条目复制到initialUnsafes中
    } else {
      falseUnsafes = new TreeSet<>(initialUnsafes);     // 首次执行到此时，initialUnsafe中包含了unrefinedIds中存在Unsafe所有的id，之后经过下面两步逐渐筛检存在falseUnsafe的id
      falseUnsafes.removeAll(unrefinedIds.keySet());    // 此时falseUnsafe中剩下的就是真正的falseUnsafe（如果是falseUnsafe，则经过之前的细化后，相应的unrefinedId从haveUnsafe变成了不在含有Unsafe，因此相应的unrefinedIds中的项被删除）
      falseUnsafes.removeAll(refinedIds.keySet());      // 放到refinedIds中的id不用再管
    }

    unsafeDetectionTimer.stop();
  }
}
