// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.states;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;

public class RCUMemoryLocation implements Comparable<RCUMemoryLocation>, Serializable {

  private static final long serialVersionUID = -8910967707373729034L;
  private final String functionName;
  private final String identifier;
  private final @Nullable Long offset;

  private RCUMemoryLocation(String pFunctionName, String pIdentifier, @Nullable Long pOffset) {
    checkNotNull(pFunctionName);
    checkNotNull(pIdentifier);

    functionName = pFunctionName;
    identifier = pIdentifier;
    offset = pOffset;
  }

  protected RCUMemoryLocation(String pIdentifier, @Nullable Long pOffset) {
    checkNotNull(pIdentifier);

    int separatorIndex = pIdentifier.indexOf("::");
    if (separatorIndex >= 0) {
      functionName = pIdentifier.substring(0, separatorIndex);
      identifier = pIdentifier.substring(separatorIndex + 2);
    } else {
      functionName = null;
      identifier = pIdentifier;
    }
    offset = pOffset;
  }

  @Override
  public boolean equals(Object other) {

    if (this == other) {
      return true;
    }

    if (!(other instanceof RCUMemoryLocation)) {
      return false;
    }

    RCUMemoryLocation otherLocation = (RCUMemoryLocation) other;

    return Objects.equals(functionName, otherLocation.functionName)
        && Objects.equals(identifier, otherLocation.identifier)
        && Objects.equals(offset, otherLocation.offset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionName, identifier, offset);
  }

  public static RCUMemoryLocation valueOf(String pFunctionName, String pIdentifier) {
    return new RCUMemoryLocation(pFunctionName, pIdentifier, null);
  }

  public static RCUMemoryLocation valueOf(String pFunctionName, String pIdentifier, long pOffset) {
    return new RCUMemoryLocation(pFunctionName, pIdentifier, pOffset);
  }

  public static RCUMemoryLocation valueOf(String pIdentifier, long pOffset) {
    return new RCUMemoryLocation(pIdentifier, pOffset);
  }

  public static RCUMemoryLocation valueOf(String pIdentifier, OptionalLong pOffset) {
    return new RCUMemoryLocation(pIdentifier, pOffset.isPresent() ? pOffset.orElseThrow() : null);
  }

  public static RCUMemoryLocation valueOf(String pVariableName) {

    List<String> nameParts = Splitter.on("::").splitToList(pVariableName);
    List<String> offsetParts = Splitter.on('/').splitToList(pVariableName);

    boolean isScoped = nameParts.size() == 2;
    boolean hasOffset = offsetParts.size() == 2;

    @Nullable Long offset = hasOffset ? Long.parseLong(offsetParts.get(1)) : null;

    if (isScoped) {
      String functionName = nameParts.get(0);
      String varName = nameParts.get(1);
      if (hasOffset) {
        varName = varName.replace("/" + offset, "");
      }
      return new RCUMemoryLocation(functionName, varName, offset);

    } else {
      String varName = nameParts.get(0);
      if (hasOffset) {
        varName = varName.replace("/" + offset, "");
      }
      return new RCUMemoryLocation(varName.replace("/" + offset, ""), offset);
    }
  }

  public String getAsSimpleString() {
    String variableName = isOnFunctionStack() ? (functionName + "::" + identifier) : identifier;
    if (offset == null) {
      return variableName;
    }
    return variableName + "/" + offset;
  }

  public String serialize() {
    return getAsSimpleString();
  }

  public boolean isOnFunctionStack() {
    return functionName != null;
  }

  public boolean isOnFunctionStack(String pFunctionName) {
    return functionName != null && pFunctionName.equals(functionName);
  }

  public String getFunctionName() {
    return checkNotNull(functionName);
  }

  public String getIdentifier() {
    return identifier;
  }

  public boolean isReference() {
    return offset != null;
  }

  /**
   * Gets the offset of a reference. Only valid for references.
   * See {@link RCUMemoryLocation#isReference()}.
   *
   * @return the offset of a reference.
   */
  public long getOffset() {
    checkState(offset != null, "memory location '%s' has no offset", this);
    return offset;
  }

  public RCUMemoryLocation getReferenceStart() {
    checkState(isReference(), "Memory location is no reference: %s", this);
    if (functionName != null) {
      return new RCUMemoryLocation(functionName, identifier, null);
    } else {
      return new RCUMemoryLocation(identifier, null);
    }
  }

  @Override
  public String toString() {
    return getAsSimpleString();
  }

  public static PersistentMap<RCUMemoryLocation, Long> transform(
      PersistentMap<String, Long> pConstantMap) {

    PersistentMap<RCUMemoryLocation, Long> result = PathCopyingPersistentTreeMap.of();

    for (Map.Entry<String, Long> entry : pConstantMap.entrySet()) {
      result = result.putAndCopy(valueOf(entry.getKey()), checkNotNull(entry.getValue()));
    }

    return result;
  }

  @Override
  public int compareTo(RCUMemoryLocation other) {
    return ComparisonChain.start()
        .compare(functionName, other.functionName, Ordering.natural().nullsFirst())
        .compare(identifier, other.identifier)
        .compare(offset, other.offset, Ordering.natural().nullsFirst())
        .result();
  }
}
