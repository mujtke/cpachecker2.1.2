package org.sosy_lab.cpachecker.cpa.racer;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cpa.racer.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.Identifiers;
import org.sosy_lab.cpachecker.util.identifiers.RacerIdentifierCreator;

public class BinderFunctionInfo {

  private static class LinkerInfo {
    private final int num;
    private final int dereference;

    LinkerInfo(int p, int d) {
      num = p;
      dereference = d;
    }
  }

  private final ImmutableList<Pair<Access, Integer>> parameterInfo;
  /*
   * 0 - before equal,
   * 1 - first parameter, etc..
   */
  private final Pair<LinkerInfo, LinkerInfo> linkInfo;

  BinderFunctionInfo() {
    // Default constructor for free functions
    linkInfo = null;
    parameterInfo = ImmutableList.of(Pair.of(Access.WRITE, 1));
  }

  @SuppressWarnings("deprecation")
  BinderFunctionInfo(String name, Configuration pConfig, LogManager pLogger) {
    try {
      String line = pConfig.getProperty(name + ".pInfo");
      Preconditions.checkNotNull(line);
      line = line.replaceAll("\\s", "");

      parameterInfo =
          from(Splitter.on(",").splitToList(line))
              .transform(s -> Splitter.on(":").splitToList(s))
              .transform(s -> Pair.of(Access.valueOf(s.get(0).toUpperCase()), getNumOrDefault(s)))
              .toList();

      line = pConfig.getProperty(name + ".linkInfo");

      if (line != null) {
        List<String> options;
        List<String> pOption;
        line = line.replaceAll("\\s", "");
        options = Splitter.on(",").splitToList(line);
        assert options.size() == 2;
        LinkerInfo[] lInfo = new LinkerInfo[2];
        for (int i = 0; i < 2; i++) {
          pOption = Splitter.on(":").splitToList(options.get(i));
          int dereference = getNumOrDefault(pOption);
          lInfo[i] = new LinkerInfo(Integer.parseInt(pOption.get(0)), dereference);
        }
        linkInfo = Pair.of(lInfo[0], lInfo[1]);
      } else {
        linkInfo = null;
      }
    } catch (NumberFormatException e) {
      pLogger.log(Level.WARNING, "No information about parameters in " + name + " function");
      throw e;
    }
  }

  public boolean shouldBeLinked() {
    return linkInfo != null;
  }

  public Collection<Pair<AbstractIdentifier, AbstractIdentifier>> constructIdentifiers(
      final CExpression left,
      final List<CExpression> params,
      RacerIdentifierCreator creator) {

    AbstractIdentifier idIn = constructIdentifier(linkInfo.getFirst(), left, params, creator);
    AbstractIdentifier idFrom = constructIdentifier(linkInfo.getFirst(), left, params, creator);

    if (idIn == null || idFrom == null) {
      return ImmutableSet.of();
    }
    return ImmutableSet.of(Pair.of(idIn, idFrom));
  }

  private AbstractIdentifier constructIdentifier(
      final LinkerInfo info,
      final CExpression left,
      final List<CExpression> params,
      RacerIdentifierCreator creator) {

    CExpression expr;

    if (info.num == 0 && left != null) {
      expr = left;
    } else if (info.num > 0) {
      expr = params.get(info.num - 1);
    } else {
      /* f.e. sdlGetFirst(), which is used for deleting element
       * we don't link, but it isn't an error
       */
      return null;
    }
    return creator.createIdentifier(expr, info.dereference);
  }

  public AbstractIdentifier createParamenterIdentifier(
      final CExpression param, int num, String currentFunction) {

    return Identifiers.createIdentifier(param, parameterInfo.get(num).getSecond(), currentFunction);
  }

  public Access getBindedAccess(int num) {
    return parameterInfo.get(num).getFirst();
  }

  private int getNumOrDefault(List<String> list) {
    if (list.size() == 1) {
      return 0;
    } else {
      return Integer.parseInt(list.get(1));
    }
  }
}

