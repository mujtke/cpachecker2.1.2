// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.racer;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class RacerState extends AbstractSingleWrapperState
    implements LatticeAbstractState<RacerState>, AliasInfoProvider, Graphable, Targetable,
               AbstractQueryableState {

  protected final StateStatistics stats;

  // TODO implements Targetable interface
  @Override
  public boolean isTarget() {
    //return super.isTarget();
    return isRaceKeyState;
  }

  @Override
  public @NonNull Set<TargetInformation> getTargetInformation() throws IllegalStateException {
    //return super.getTargetInformation();
    return new HashSet<TargetInformation>();
  }

  protected ImmutableMap<AbstractIdentifier, AbstractIdentifier> variableBindingRelation;
  private boolean isRaceKeyState;

  protected RacerState(
      final AbstractState pWrappedState,
      final ImmutableMap<AbstractIdentifier, AbstractIdentifier> pVarBind,
      final StateStatistics pStats) {
    super(pWrappedState);
    variableBindingRelation = pVarBind;
    stats = pStats;
    pStats.statCounter.setNextValue(variableBindingRelation.size());
    isRaceKeyState = false;
  }

  public static RacerState createInitialState(final AbstractState pWrappedState) {
    return new RacerState(pWrappedState, ImmutableMap.of(), new StateStatistics());
  }

  protected RacerState createState(
      final AbstractState pWrappedState,
      final ImmutableMap<AbstractIdentifier, AbstractIdentifier> pVarBind,
      final StateStatistics pStats) {
    return new RacerState(pWrappedState, pVarBind, pStats);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hashCode(variableBindingRelation);
    result = prime * result + super.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RacerState)) {
      return false;
    }
    RacerState other = (RacerState) obj;
    return
        Objects.equals(variableBindingRelation, other.variableBindingRelation)
            && getWrappedState().equals(other.getWrappedState());
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();

    str.append("[");
    Joiner.on(", ").withKeyValueSeparator("->").appendTo(str, variableBindingRelation);
    str.append("]\n");
    str.append(getWrappedState());
    return str.toString();
  }

  @Override
  public RacerState join(RacerState other) throws CPAException, InterruptedException {
    stats.joinTimer.start();

    ImmutableMap.Builder<AbstractIdentifier, AbstractIdentifier> newRelation = ImmutableMap.builder();
    newRelation.putAll(variableBindingRelation);

    for (Entry<AbstractIdentifier, AbstractIdentifier> entry : other.variableBindingRelation.entrySet()) {
      if (variableBindingRelation.containsKey(entry.getKey())) {
        if (!variableBindingRelation.get(entry.getKey()).equals(entry.getValue())) {
          throw new UnsupportedOperationException("Joining states with the same variable binded to the different variables is not supported yet");
        } else {
          newRelation.put(entry.getKey(), entry.getValue());
        }
      }
    }
    stats.joinTimer.stop();
    return createState(this.getWrappedState(), newRelation.build(), stats);
  }

  @Override
  public boolean isLessOrEqual(RacerState other) throws CPAException, InterruptedException {
    stats.lessTimer.start();
    if (this.variableBindingRelation.size() > other.variableBindingRelation.size()) {
      stats.lessTimer.stop();
      return false;
    }
    if (from(variableBindingRelation.keySet())
        .anyMatch(Predicates.not(other.variableBindingRelation::containsKey))) {
      stats.lessTimer.stop();
      return false;
    }
    stats.lessTimer.stop();
    return true;
  }

  @Override
  public String toDOTLabel() {
    return this.getWrappedState() instanceof Graphable ? ((Graphable)this.getWrappedState()).toDOTLabel() : "";
  }

  @Override
  public boolean shouldBeHighlighted() {
    return true;
  }

  @Override
  public Collection<AbstractIdentifier> getAllPossibleAliases(AbstractIdentifier id) {
    AbstractIdentifier newId = getLinksIfNecessary(id);
    if (newId != id) {
      return ImmutableSet.of(newId);
    } else {
      return ImmutableSet.of();
    }
  }

  @Override
  public void filterAliases(
      AbstractIdentifier pIdentifier, Collection<AbstractIdentifier> pSet) {
    AbstractIdentifier newId = getLinksIfNecessary(pIdentifier);
    if (newId != pIdentifier) {
      pSet.remove(pIdentifier);
    }
  }

  // implements AbstractQueryableState interface
  @Override
  public String getCPAName() {
    return "RacerUsageCPA";
  }

  public void setRaceKeyState(boolean pRaceKeyState) {
    isRaceKeyState = pRaceKeyState;
  }

  @Override
  public boolean checkProperty(String property) throws InvalidQueryException {
    //return AbstractQueryableState.super.checkProperty(property);
    if (property.equalsIgnoreCase("data-race")) {
      return isRaceKeyState;
    } else {
      throw new InvalidQueryException("The Query \"" + property + "\" is invalid.");
    }
  }

  @Override
  public Object evaluateProperty(String property) throws InvalidQueryException {
    return checkProperty(property);
  }

  @Override
  public void modifyProperty(String modification) throws InvalidQueryException {
    AbstractQueryableState.super.modifyProperty(modification);
  }

  public static class StateStatistics {
    private StatTimer joinTimer = new StatTimer("Time for joining");
    private StatTimer lessTimer = new StatTimer("Time for cover check");
    private StatInt statCounter =
        new StatInt(StatKind.SUM, "Sum of variableBindingRelation's sizes");

    private StatTimer reduceExpandTimer = new StatTimer("Time for reducing and expanding");
    private StatInt reducedBindungs =
        new StatInt(StatKind.AVG, "Average variableBindingRelation's sizes reducing");

    public StateStatistics() {}

    public void printStatistics(StatisticsWriter out) {
      out.spacer()
          .put(joinTimer)
          .put(lessTimer)
          .put(statCounter)
          .put(reduceExpandTimer)
          .put(reducedBindungs);
    }
  }

  public static RacerState get(AbstractState state) {
    return AbstractStates.extractStateByType(state, RacerState.class);
  }


  public RacerState put(Collection<Pair<AbstractIdentifier, AbstractIdentifier>> newLinks) {
    ImmutableMap<AbstractIdentifier, AbstractIdentifier> newMap = variableBindingRelation;

    for (Pair<AbstractIdentifier, AbstractIdentifier> pair : newLinks) {
      newMap = put(pair, newMap);
    }

    if (newMap == variableBindingRelation) {
      return this;
    } else {
      return createState(this.getWrappedState(), newMap, stats);
    }
  }

  private ImmutableMap<AbstractIdentifier, AbstractIdentifier> put(
      Pair<AbstractIdentifier, AbstractIdentifier> pair,
      ImmutableMap<AbstractIdentifier, AbstractIdentifier> newMap) {

    AbstractIdentifier id1 = pair.getFirst();
    AbstractIdentifier id2 = getLinksIfNecessary(pair.getSecond());
    ImmutableMap<AbstractIdentifier, AbstractIdentifier> result = newMap;

    if (!id1.equals(id2)) {
      AbstractIdentifier newId1 = id1.cloneWithDereference(0);
      AbstractIdentifier newId2 =
          id2.cloneWithDereference(id2.getDereference() - id1.getDereference());
      ImmutableMap.Builder<AbstractIdentifier, AbstractIdentifier> builder = ImmutableMap.builder();
      boolean new_entry = true;

      // If there was already an entry with same first AbstractIdentifier in
      // variableBindingRelation,
      // change it.
      for (Entry<AbstractIdentifier, AbstractIdentifier> entry : newMap.entrySet()) {
        AbstractIdentifier key = entry.getKey();
        if (key.equals(newId1)) {
          if (entry.getValue().equals(newId2)) {
            // Nothing changed
            return result;
          }
          // Can not remove from builder, so have to go through a map manually
          builder.put(newId1, newId2);
          new_entry = false;
        } else {
          builder.put(entry);
        }
      }
      // If this is an entry with new first AbstractIdentifier, add it.
      if (new_entry) {
        builder.put(newId1, newId2);
      }
      result = builder.build();
    }
    return result;
  }


  private AbstractIdentifier getLinksIfNecessary(final AbstractIdentifier id) {
    /* Special get!
     * If we get **b, having (*b, c), we give *c
     */
    AbstractIdentifier newId = id.cloneWithDereference(0);
    AbstractIdentifier returnIdentifier;
    if (variableBindingRelation.containsKey(newId)) {
      AbstractIdentifier initialId = variableBindingRelation.get(newId);
      AbstractIdentifier pointsTo =
          initialId.cloneWithDereference(initialId.getDereference() + id.getDereference());
      if (newId.compareTo(initialId.cloneWithDereference(0)) != 0) {
        returnIdentifier = getLinksIfNecessary(pointsTo);
      } else {
        returnIdentifier = pointsTo;
      }
    } else {
      returnIdentifier = id;
    }

    if (returnIdentifier instanceof StructureIdentifier) {
      StructureIdentifier rid = (StructureIdentifier) returnIdentifier;
      AbstractIdentifier newOwner = getLinksIfNecessary(rid.getOwner());
      if (newOwner.compareTo(rid.getOwner()) != 0) {
        returnIdentifier =
            new StructureIdentifier(rid.getName(), rid.getType(), rid.getDereference(), newOwner);
      }
    }
    return returnIdentifier;
  }

  public RacerState copy(final AbstractState pWrappedState) {
    return new RacerState(pWrappedState, this.variableBindingRelation, this.stats);
  }


  public RacerState removeInternalLinks(final String functionName) {
    boolean noRemove = true;
    ImmutableMap.Builder<AbstractIdentifier, AbstractIdentifier> builder = ImmutableMap.builder();
    for (Entry<AbstractIdentifier, AbstractIdentifier> entry : variableBindingRelation.entrySet()) {
      AbstractIdentifier key = entry.getKey();
      if (key instanceof LocalVariableIdentifier
          && ((LocalVariableIdentifier) key).getFunction().equals(functionName)) {
        noRemove = false;
      } else {
        builder.put(entry);     // 将与局部变量相关的变量绑定从绑定关系中去除
      }
    }
    if (noRemove) {   //如果全都是与全局变量相关的变量绑定关系，则noRemove为true
      return this;
    }
    return createState(this.getWrappedState(), builder.build(), stats);
  }

  public StateStatistics getStatistics() {
    return stats;
  }
}
