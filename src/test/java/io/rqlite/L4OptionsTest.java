package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Level;
import io.rqlite.client.L4Options;
import io.rqlite.jdbc.L4Conn;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.Properties;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4OptionsTest {

  static {
    it("Keeps options independent per instance", () -> {
      var a = new L4Options();
      var b = new L4Options();
      b.level = L4Level.none;
      b.timeoutSec = 42;
      b.freshnessSec = 1;

      assertTrue(a.queryParams(false).contains("level=linearizable"));
      assertTrue(b.queryParams(false).contains("level=none"));
      assertTrue(a.queryParams(false).contains("timeout=5s"));
      assertTrue(b.queryParams(false).contains("timeout=42s"));
    });

    it("Does not share timeout state across clients", () -> {
      var c1 = new L4Client("http://localhost:4001", null, new L4Options());
      var c2 = new L4Client("http://localhost:4001", null, new L4Options());

      c1.withTxTimeoutSec(10);

      assertEquals(10, c1.getTxTimeoutSec());
      assertEquals(5, c2.getTxTimeoutSec());
    });

    it("Parses connection properties into a scoped options instance", () -> {
      var p = new Properties();
      p.setProperty(L4Options.kBaseUrl, "http://example:4001");
      p.setProperty(L4Options.kLevel, "strong");
      p.setProperty(L4Options.kTimeoutSec, "9");
      var o = new L4Options(p);

      assertEquals("http://example:4001", o.baseUrl);
      assertEquals(L4Level.strong, o.level);
      assertEquals(9, o.timeoutSec);

      var d = new L4Options();
      assertNull(d.baseUrl);
      assertEquals(L4Level.linearizable, d.level);
      assertEquals(5, d.timeoutSec);
    });

    it("Scopes connection network timeout to its own client", () -> {
      var c1 = new L4Client("http://localhost:4001", null, new L4Options());
      var c2 = new L4Client("http://localhost:4001", null, new L4Options());
      var conn1 = new L4Conn(c1);
      var conn2 = new L4Conn(c2);

      conn1.setNetworkTimeout(Runnable::run, 12000);

      assertEquals(12000, conn1.getNetworkTimeout());
      assertEquals(5000, conn2.getNetworkTimeout());
      assertEquals(12, c1.getOptions().timeoutSec);
      assertEquals(5, c2.getOptions().timeoutSec);
    });
  }

}
