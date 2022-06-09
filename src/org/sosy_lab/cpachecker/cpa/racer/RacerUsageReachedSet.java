// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.SimpleTargetInformation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Targetable.TargetInformation;
import org.sosy_lab.cpachecker.core.reachedset.RacerPartitionedReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPA;
import org.sosy_lab.cpachecker.cpa.racer.storage.RacerUsageConfiguration;
import org.sosy_lab.cpachecker.cpa.racer.storage.RacerUsageContainer;
import org.sosy_lab.cpachecker.cpa.racer.storage.RacerUsageExtractor;
import org.sosy_lab.cpachecker.cpa.racer.RacerState;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo;
import org.sosy_lab.cpachecker.cpa.racerThreading.RacerThreadingState;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPA;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@SuppressFBWarnings(justification = "No support for serialization", value = "SE_BAD_FIELD")
public class RacerUsageReachedSet extends RacerPartitionedReachedSet {

  private static final long serialVersionUID = 1L;

  private boolean usagesExtracted = false;
  private boolean haveUnsafes = false;

  private static final ImmutableSet<TargetInformation> RACE_PROPERTY =
      SimpleTargetInformation.singleton("Race condition");

  private final LogManager logger;
  private final RacerUsageConfiguration usageConfiguration;
  private RacerUsageExtractor serialExtractor = null;           // 提取Usage信息

  private final RacerUsageContainer container;                  // Usage容器

  public LinkedHashMap<AbstractState, Precision> newSuccessorsInEachIteration;        // 用来存储每次迭代新产生的后继

  //public HashMap<AbstractState, Precision> coveredStatesTable;                   // 用来存放因为被覆盖的状态，这些状态可能在后面的过程中重新放回waitList中，使用HashMap的话放入其中的状态顺序是随机的
  public TreeMap<AbstractState, Precision> coveredStatesTable;                   // 用来存放因为被覆盖的状态，这些状态可能在后面的过程中重新放回waitList中

  public AdjustablePrecision finalPrecision = new AdjustablePrecision() {             // 用来收集细化过程中产生的精度
    @Override
    public AdjustablePrecision add(AdjustablePrecision otherPrecision) {
      return null;
    }

    @Override
    public AdjustablePrecision subtract(AdjustablePrecision otherPrecision) {
      return null;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  };

  // 用来记录处理过的Unsafes
  public Set<SingleIdentifier> processedUnsafes;

  // 复制identifierIterator中的precisionMap
  public final Map<SingleIdentifier, AdjustablePrecision> precisionMap = new HashMap<>();

  // 记录是否还有covered的状态
  public boolean stillHaveCoveredStates = false;

  // 记录已经访问过的位置
  public static Set<CFANode> visitedLocations = new HashSet<>();

  // 记录被cover的状态
  public List<AbstractState> coveredStates = new ArrayList<>();

  public RacerUsageReachedSet(
      ConfigurableProgramAnalysis pCpa, WaitlistFactory waitlistFactory, RacerUsageConfiguration pConfig, LogManager pLogger) {
    super(pCpa, waitlistFactory);
    logger = pLogger;
    container = new RacerUsageContainer(pConfig, logger);
    usageConfiguration = pConfig;
    newSuccessorsInEachIteration = new LinkedHashMap<>();
    processedUnsafes = new HashSet<>();
    coveredStatesTable = new TreeMap<>();
    serialExtractor = new RacerUsageExtractor(pCpa, logger, container, usageConfiguration);
  }

  @Override
  public void remove(AbstractState pState) {
    super.remove(pState);
    if (container != null) {
      RacerState ustate = RacerState.get(pState);
      container.removeState(ustate);
    }
  }

  @Override
  public void add(AbstractState pState, Precision pPrecision) {
    super.add(pState, pPrecision);
  }

  @Override
  public void clear() {
    container.resetUnrefinedUnsafes();
    usagesExtracted = false;
    super.clear();
  }

  public void finalize(ConfigurableProgramAnalysis pCpa) {
    /*
    BAMCPA bamCPA = CPAs.retrieveCPA(pCpa, BAMCPA.class);
    if (bamCPA != null) {
      UsageCPA uCpa = CPAs.retrieveCPA(pCpa, UsageCPA.class);
      uCpa.getStats().setBAMCPA(bamCPA);
    }
    */
    serialExtractor = new RacerUsageExtractor(pCpa, logger, container, usageConfiguration);
  }


  /**
   * 将covered State放入reachedSet中，但是不会将其放入waitlist中去
   * @param pState
   * @param pPrecision
   */
  public void addButSkipWaitlist(AbstractState pState, Precision pPrecision) {
    super.addButSkipWaitlist(pState, pPrecision);
  }

  public boolean isStillHaveCoveredStates() {
    return !coveredStatesTable.isEmpty();
  }

  public void rollbackCoveredStates() {
    // TODO
    for (Map.Entry<AbstractState, Precision> s : coveredStatesTable.entrySet()) {
      ARGState pState = (ARGState) s.getKey();
      RacerThreadingState tState = extractStateByType(pState, RacerThreadingState.class);
      if (tState.locationCovered) {    // 如果满足位置覆盖
        putBackToWaitlist(pState);   // 在DefaultReachedSet中添加putBackToWaitlist方法
        // for debug， 放回waitlist的状态，需要将其重新放入reached中(不使用add，而是使用addButSkipWaitlist方法跳过将状态放回waitlist中)
        addButSkipWaitlist(s.getKey(), s.getValue());
      }
    }
    coveredStatesTable.clear(); // 清空coveredStatesTable，用于下一次的计算
    visitedLocations.clear();
  }

  public void printStatistics(StatisticsWriter pWriter) {
    serialExtractor.printSatistics(pWriter);
  }

  public RacerUsageContainer getUsageContainer() {
    return container;
  }

  // 检查每轮得到的后继是否导致了不安全
  // 重写extractUsages方法
  public boolean haveUnsafeInNewSucs() {
    serialExtractor.extractUsages(newSuccessorsInEachIteration);        // 从新探索得到的后继中提取Usage
    if (serialExtractor.haveUsagesExtracted) {                          // 如果有Usage被提取出的话
      serialExtractor.haveUsagesExtracted = false;
      return container.hasUnsafesForUsageContainer();                 // 提取出Usage之后，判断是否发现了不安全
    }
    return false;
  }

  public Map<SingleIdentifier, Pair<UsageInfo, UsageInfo>> getUnsafes() {
    return container.getStableUnsafes();
  }

  public void setHaveUnsafes(boolean value) {
    haveUnsafes = value;
  }
}
