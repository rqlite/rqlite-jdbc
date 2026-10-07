package io.rqlite;

import io.rqlite.jdbc.L4Log;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4LogTest {

  static {
    it("Routes trace and debug to their own loggers", () -> {
      var traceLines = new ArrayList<String>();
      var debugLines = new ArrayList<String>();
      L4Log.setTraceLogger((fmt, args) -> traceLines.add(fmt));
      L4Log.setDebugLogger((fmt, args) -> debugLines.add(fmt));

      L4Log.trace("trace {}", 1);
      L4Log.debug("debug {}", 2);

      assertEquals(List.of("trace {}"), traceLines);
      assertEquals(List.of("debug {}"), debugLines);
    });
  }

}
