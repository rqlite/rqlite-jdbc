package io.rqlite;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.sql.*;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

/**
 * Exercises the reconciliation between the JDBC transaction contract and rqlite's
 * atomic-batch transactions, bulk/batch CRUD, and read-consistency modes.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4TransactionTest {

  static {
    if (L4Tests.runIntegrationTests) {
      beforeAll(() -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          stmt.execute("DROP TABLE IF EXISTS tx_test");
          stmt.execute("CREATE TABLE tx_test (id INTEGER PRIMARY KEY, name TEXT)");
        }
      });

      it("Treats commit and rollback leniently in auto-commit mode", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          assertTrue(conn.getAutoCommit());
          conn.commit();   // no-op, for change-set tool compatibility
          conn.rollback(); // no-op, for change-set tool compatibility
          conn.setAutoCommit(false);
          assertFalse(conn.getAutoCommit());
          conn.commit();   // valid within a transaction
          conn.setAutoCommit(true);
          assertTrue(conn.getAutoCommit());
        }
      });

      it("Commits deferred statements and does not provide read-your-writes", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setAutoCommit(false);
          var stmt = conn.createStatement();
          stmt.executeUpdate("INSERT INTO tx_test (id, name) VALUES (1, 'a')");
          stmt.executeUpdate("INSERT INTO tx_test (id, name) VALUES (2, 'b')");
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id IN (1, 2)")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c")); // pending writes are not visible
          }
          conn.commit();
          conn.setAutoCommit(true);
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id IN (1, 2)")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("c"));
          }
          conn.createStatement().executeUpdate("DELETE FROM tx_test WHERE id IN (1, 2)");
        }
      });

      it("Discards deferred statements on rollback", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setAutoCommit(false);
          conn.createStatement().executeUpdate("INSERT INTO tx_test (id, name) VALUES (10, 'rollback')");
          conn.rollback();
          conn.setAutoCommit(true);
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id = 10")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"));
          }
        }
      });

      it("Commits on setAutoCommit(true) and discards on close", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setAutoCommit(false);
          conn.createStatement().executeUpdate("INSERT INTO tx_test (id, name) VALUES (20, 'auto')");
          conn.setAutoCommit(true);
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id = 20")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("c"));
          }
          conn.createStatement().executeUpdate("DELETE FROM tx_test WHERE id = 20");
        }
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setAutoCommit(false);
          conn.createStatement().executeUpdate("INSERT INTO tx_test (id, name) VALUES (30, 'discard')");
          conn.close();
        }
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id = 30")) {
          assertTrue(rs.next());
          assertEquals(0, rs.getInt("c"));
        }
      });

      it("Supports DDL inside a transaction", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setAutoCommit(false);
          var stmt = conn.createStatement();
          stmt.execute("CREATE TABLE tx_ddl (id INTEGER PRIMARY KEY, v TEXT)");
          stmt.executeUpdate("INSERT INTO tx_ddl (id, v) VALUES (1, 'x')");
          conn.commit();
          conn.setAutoCommit(true);
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_ddl")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("c"));
          }
          conn.createStatement().execute("DROP TABLE tx_ddl");
        }
      });

      it("Treats setReadOnly leniently", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          conn.setReadOnly(true); // no-op hint, even during a transaction
          assertFalse(conn.isReadOnly());
          conn.setAutoCommit(false);
          conn.setReadOnly(false); // no-op
          conn.rollback();
          conn.setAutoCommit(true);
        }
      });

      it("Enforces the transaction isolation API", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          assertEquals(Connection.TRANSACTION_SERIALIZABLE, conn.getTransactionIsolation());
          conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
          for (var level : new int[] {
            Connection.TRANSACTION_NONE,
            Connection.TRANSACTION_READ_UNCOMMITTED,
            Connection.TRANSACTION_READ_COMMITTED,
            Connection.TRANSACTION_REPEATABLE_READ
          }) {
            try {
              conn.setTransactionIsolation(level);
              fail("Expected unsupported isolation level: " + level);
            } catch (SQLException e) {
              assertEquals("0A000", e.getSQLState());
            }
          }
          var meta = conn.getMetaData();
          assertTrue(meta.supportsTransactions());
          assertEquals(Connection.TRANSACTION_SERIALIZABLE, meta.getDefaultTransactionIsolation());
          assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE));
          assertFalse(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED));
          assertTrue(meta.supportsDataDefinitionAndDataManipulationTransactions());
          assertFalse(meta.supportsSavepoints());
          assertTrue(meta.supportsBatchUpdates());
          try {
            conn.setSavepoint();
            fail("setSavepoint must be unsupported");
          } catch (SQLException e) {
            assertEquals("0A000", e.getSQLState());
          }
        }
      });

      it("Rolls back a whole batch when any statement fails", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          stmt.addBatch("INSERT INTO tx_test (id, name) VALUES (40, 'ok')");
          stmt.addBatch("INSERT INTO nonexistent_table (id) VALUES (1)");
          try {
            stmt.executeBatch();
            fail("Expected BatchUpdateException");
          } catch (SQLException e) {
            assertNotNull(e.getMessage());
          }
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id = 40")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c")); // atomic rollback
          }
        }
      });

      it("Fails fast when a batch exceeds maxBatchStatements", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?maxBatchStatements=1");
             var stmt = conn.createStatement()) {
          stmt.addBatch("INSERT INTO tx_test (id, name) VALUES (50, 'a')");
          stmt.addBatch("INSERT INTO tx_test (id, name) VALUES (51, 'b')");
          try {
            stmt.executeBatch();
            fail("Expected SQLException for oversized batch");
          } catch (SQLException e) {
            assertEquals("22003", e.getSQLState());
          }
        }
      });

      it("Reads committed data under every consistency level", () -> {
        for (var level : new String[] { "none", "weak", "strong", "linearizable", "auto" }) {
          try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?level=" + level);
               var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM Artist")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt("c") >= 1);
            // Read consistency is orthogonal to the JDBC isolation level.
            assertEquals(Connection.TRANSACTION_SERIALIZABLE, conn.getTransactionIsolation());
          }
        }
      });

      it("Supports freshness and linearizable timeout options", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?level=none&freshnessSec=1&freshnessStrict=true");
             var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM Artist")) {
          assertTrue(rs.next());
        }
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?level=linearizable&linearizableTimeoutSec=2");
             var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM Artist")) {
          assertTrue(rs.next());
        }
      });

      it("Handles queued writes without counts", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?queue=true&wait=true");
             var stmt = conn.createStatement()) {
          // Queued responses carry no per-statement results, so counts are unknown.
          assertEquals(0, stmt.executeUpdate("INSERT INTO tx_test (id, name) VALUES (60, 'queued')"));

          stmt.addBatch("INSERT INTO tx_test (id, name) VALUES (61, 'queued-a')");
          stmt.addBatch("INSERT INTO tx_test (id, name) VALUES (62, 'queued-b')");
          assertArrayEquals(
            new int[] { Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO },
            stmt.executeBatch());
        }
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM tx_test WHERE id IN (60, 61, 62)")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("c"));
          }
          conn.createStatement().executeUpdate("DELETE FROM tx_test WHERE id IN (60, 61, 62)");
        }
      });
    }
  }

}
