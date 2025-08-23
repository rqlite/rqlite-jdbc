package io.rqlite.jdbc;

import java.util.Objects;
import java.util.function.BiConsumer;

public class L4Log {

  public static BiConsumer<String, Object[]> traceFn, debugFn, infoFn, warnFn;

  public static void setTraceLogger(BiConsumer<String, Object[]> logFn) {
    L4Log.debugFn = Objects.requireNonNull(logFn);
  }

  public static void setDebugLogger(BiConsumer<String, Object[]> logFn) {
    L4Log.debugFn = Objects.requireNonNull(logFn);
  }

  public static void setInfoLogger(BiConsumer<String, Object[]> logFn) {
    L4Log.infoFn = Objects.requireNonNull(logFn);
  }

  public static void setWarnLogger(BiConsumer<String, Object[]> logFn) {
    L4Log.warnFn = Objects.requireNonNull(logFn);
  }

  public static void trace(String fmt, Object ... args) {
    if (debugFn != null) {
      debugFn.accept(fmt, args);
    }
  }

  public static void debug(String fmt, Object ... args) {
    if (debugFn != null) {
      debugFn.accept(fmt, args);
    }
  }

  public static void info(String fmt, Object ... args) {
    if (infoFn != null) {
      infoFn.accept(fmt, args);
    }
  }

  public static void warn(String fmt, Object ... args) {
    if (warnFn != null) {
      warnFn.accept(fmt, args);
    }
  }

}
