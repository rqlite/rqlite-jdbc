package io.rqlite.jdbc;

import java.util.function.BiConsumer;

public class L4Log {

  public static BiConsumer<String, Object[]> debugFn, traceFn;

  public static void l4Debug(String format, Object ... args) {
    if (debugFn != null) {
      debugFn.accept(format, args);
    }
  }

  public static void l4Trace(String format, Object ... args) {
    if (traceFn != null) {
      traceFn.accept(format, args);
    }
  }

}
