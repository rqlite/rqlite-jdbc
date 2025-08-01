package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Options;
import io.rqlite.client.L4Statement;
import io.rqlite.jdbc.L4St;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.sql.*;
import java.util.*;

import static io.rqlite.jdbc.L4Err.*;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4StTest {

  private static final L4Client rq = L4Tests.localClient();

  // Setup helper to create and populate test table
  private static void setupTestTable(L4Client rq) throws Exception {
    // Drop existing table
    var dr = rq.executeSingle("DROP TABLE IF EXISTS st_test_data");
    assertEquals(200, dr.statusCode);

    // Create table with diverse data types
    var createTable = String.join("\n", "",
      "CREATE TABLE st_test_data (",
      "  id INTEGER PRIMARY KEY AUTOINCREMENT,",
      "  num_val NUMERIC,",
      "  bool_val BOOLEAN,",
      "  text_val VARCHAR,",
      "  blob_val BLOB",
      ")");
    var res0 = rq.executeSingle(createTable);
    assertEquals(200, res0.statusCode);

    // Insert test data
    var insertSql = "INSERT INTO st_test_data (num_val, bool_val, text_val, blob_val) VALUES (?, ?, ?, ?)";
    var blobData = Base64.getEncoder().encodeToString("Test blob".getBytes());
    var res2 = rq.execute(
      true,
      new L4Statement().sql(insertSql).withPositionalParams(
        123.45, 1, "Hello, world!", blobData
      ),
      new L4Statement().sql(insertSql).withPositionalParams(
        0.0, 0, null, null
      )
    );
    assertEquals(200, res2.statusCode);
  }

  static {
    if (L4Tests.runIntegrationTests) {
      it("Tests L4St query execution and result navigation", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        assertNull(stmt.getWarnings());

        // Test executeQuery with single result
        var rs = stmt.executeQuery("SELECT * FROM st_test_data WHERE id = 1");
        assertNotNull(rs);
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals(123.45, rs.getDouble("num_val"), 0.001);
        assertTrue(rs.getBoolean("bool_val"));
        assertEquals("Hello, world!", rs.getString("text_val"));
        assertFalse(rs.next());
        rs.close();

        // Test executeQuery with multiple results (multi-statement query)
        rs = stmt.executeQuery("SELECT * FROM st_test_data WHERE id = 1; SELECT * FROM st_test_data WHERE id = 2");
        assertNotNull(rs);
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertFalse(rs.next());
        assertTrue(stmt.getMoreResults());
        rs = stmt.getResultSet();
        assertNotNull(rs);
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertFalse(rs.next());
        assertFalse(stmt.getMoreResults());
        assertNull(stmt.getResultSet());
        assertEquals(-1, stmt.getUpdateCount());
        rs.close();

        // Test executeQuery with empty result
        rs = stmt.executeQuery("SELECT * FROM st_test_data WHERE id = 999");
        assertNotNull(rs);
        assertFalse(rs.next());
        assertNull(stmt.getWarnings());
        rs.close();

        // Test executeQuery with invalid SQL
        rs = stmt.executeQuery("SELECT * FROM nonexistent_table");
        assertNotNull(rs.getWarnings());
        stmt.close();
      });

      it("Tests L4St update operations", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test executeUpdate
        int rowsAffected = stmt.executeUpdate("UPDATE st_test_data SET num_val = 999.99 WHERE id = 1");
        assertEquals(1, rowsAffected);
        var rs = stmt.executeQuery("SELECT num_val FROM st_test_data WHERE id = 1");
        assertTrue(rs.next());
        assertEquals(999.99, rs.getDouble("num_val"), 0.001);
        rs.close();

        // Test executeUpdate with no rows affected
        rowsAffected = stmt.executeUpdate("UPDATE st_test_data SET num_val = 999.99 WHERE id = 999");
        assertEquals(0, rowsAffected);

        // Test executeUpdate with invalid SQL
        stmt.executeUpdate("UPDATE nonexistent_table SET num_val = 1");
        assertNotNull(stmt.getWarnings());
        stmt.close();
      });

      it("Tests L4St execute method", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test execute returning ResultSet
        var hasResultSet = stmt.execute("SELECT * FROM st_test_data WHERE id = 1");
        assertTrue(hasResultSet);
        var rs = stmt.getResultSet();
        assertNotNull(rs);
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals(-1, stmt.getUpdateCount());
        rs.close();

        // Test execute returning update count
        hasResultSet = stmt.execute("UPDATE st_test_data SET num_val = 888.88 WHERE id = 1");
        assertFalse(hasResultSet);
        assertNull(stmt.getResultSet());
        assertEquals(1, stmt.getUpdateCount());

        // Test execute with multi-statement query
        hasResultSet = stmt.execute("SELECT * FROM st_test_data WHERE id = 1; SELECT * FROM st_test_data WHERE id = 2");
        assertTrue(hasResultSet);
        rs = stmt.getResultSet();
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        rs.close();
        assertTrue(stmt.getMoreResults());
        rs = stmt.getResultSet();
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        rs.close();
        assertFalse(stmt.getMoreResults());
        assertEquals(-1, stmt.getUpdateCount());

        // Test execute with empty result
        hasResultSet = stmt.execute("SELECT * FROM st_test_data WHERE id = 999");
        assertTrue(hasResultSet);
        rs = stmt.getResultSet();
        assertFalse(rs.next());
        rs.close();

        stmt.close();
      });

      it("Tests L4St batch execution", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test batch with updates
        stmt.addBatch("UPDATE st_test_data SET num_val = 111.11 WHERE id = 1");
        stmt.addBatch("UPDATE st_test_data SET num_val = 222.22 WHERE id = 2");
        int[] updateCounts = stmt.executeBatch();
        assertArrayEquals(new int[]{1, 1}, updateCounts);
        var rs = stmt.executeQuery("SELECT num_val FROM st_test_data ORDER BY id");
        assertTrue(rs.next());
        assertEquals(111.11, rs.getDouble("num_val"), 0.001);
        assertTrue(rs.next());
        assertEquals(222.22, rs.getDouble("num_val"), 0.001);
        rs.close();

        // Test empty batch
        stmt.clearBatch();
        updateCounts = stmt.executeBatch();
        assertArrayEquals(new int[0], updateCounts);

        // Test batch with error
        stmt.addBatch("UPDATE st_test_data SET num_val = 333.33 WHERE id = 1");
        stmt.addBatch("UPDATE nonexistent_table SET num_val = 1");
        try {
          stmt.executeBatch();
          fail("Expected BatchUpdateException");
        } catch (SQLException e) {
          assertEquals(SqlStateConnectionError, e.getSQLState());
          var bue = (BatchUpdateException) e.getCause();
          assertArrayEquals(new int[]{1, 0}, bue.getUpdateCounts());
        }

        stmt.close();
      });

      it("Tests L4St closeOnCompletion", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test closeOnCompletion
        assertFalse(stmt.isCloseOnCompletion());
        stmt.closeOnCompletion();
        assertTrue(stmt.isCloseOnCompletion());
        var rs = stmt.executeQuery("SELECT * FROM st_test_data WHERE id = 1");
        assertFalse(stmt.isClosed());
        rs.close();
        assertTrue(stmt.isClosed());

        // Test closeOnCompletion with no ResultSet
        stmt = new L4St(rq);
        stmt.closeOnCompletion();
        stmt.executeUpdate("UPDATE st_test_data SET num_val = 444.44 WHERE id = 1");
        assertFalse(stmt.isClosed()); // No ResultSet, so not closed
        stmt.close();
        assertTrue(stmt.isClosed());

        // Test closeOnCompletion with closed statement
        stmt = new L4St(rq);
        stmt.close();
        try {
          stmt.closeOnCompletion();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
        try {
          stmt.isCloseOnCompletion();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
      });

      it("Tests L4St timeout handling", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test default timeout (0, no timeout)
        assertEquals(L4Options.timeoutSec, stmt.getQueryTimeout());
        stmt.setQueryTimeout(10);
        assertEquals(10, stmt.getQueryTimeout());
        var rs = stmt.executeQuery("SELECT * FROM st_test_data");
        assertTrue(rs.next());
        rs.close();

        // Test zero timeout
        stmt.setQueryTimeout(0);
        assertEquals(0, stmt.getQueryTimeout());
        rs = stmt.executeQuery("SELECT * FROM st_test_data");
        assertTrue(rs.next());
        rs.close();

        // Test negative timeout
        try {
          stmt.setQueryTimeout(-1);
          fail("Expected SQLException for negative timeout");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }

        stmt.close();
      });

      it("Tests L4St max rows and fetch size", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test max rows
        stmt.setMaxRows(1);
        assertEquals(1, stmt.getMaxRows());
        var rs = stmt.executeQuery("SELECT * FROM st_test_data");
        assertTrue(rs.next());
        assertFalse(rs.next()); // Limited to 1 row
        rs.close();

        // Test zero max rows (no limit)
        stmt.setMaxRows(0);
        assertEquals(0, stmt.getMaxRows());
        rs = stmt.executeQuery("SELECT * FROM st_test_data");
        assertTrue(rs.next());
        assertTrue(rs.next());
        assertFalse(rs.next());
        rs.close();

        // Test negative max rows
        try {
          stmt.setMaxRows(-1);
          fail("Expected SQLException for negative max rows");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidParam, e.getSQLState());
        }

        // Test fetch size
        stmt.setFetchSize(10);
        assertEquals(10, stmt.getFetchSize());
        rs = stmt.executeQuery("SELECT * FROM st_test_data");
        assertTrue(rs.next());
        rs.close();

        // Test negative fetch size
        try {
          stmt.setFetchSize(-1);
          fail("Expected SQLException for negative fetch size");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidAttr, e.getSQLState());
        }

        stmt.close();
      });

      it("Tests L4St unsupported operations and error handling", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test unsupported methods
        try {
          stmt.getMaxFieldSize();
          fail("Expected SQLException for getMaxFieldSize");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.setMaxFieldSize(100);
          fail("Expected SQLException for setMaxFieldSize");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.cancel();
          fail("Expected SQLException for cancel");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.setCursorName("cursor");
          fail("Expected SQLException for setCursorName");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.setFetchDirection(ResultSet.FETCH_REVERSE);
          fail("Expected SQLException for setFetchDirection");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.getFetchDirection();
          fail("Expected SQLException for getFetchDirection");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.getGeneratedKeys();
          fail("Expected SQLException for getGeneratedKeys");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.executeUpdate("INSERT INTO st_test_data (num_val) VALUES (1)", Statement.RETURN_GENERATED_KEYS);
          fail("Expected SQLException for generated keys");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.setPoolable(true);
          fail("Expected SQLException for setPoolable");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          stmt.isPoolable();
          fail("Expected SQLException for isPoolable");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test getMoreResults with unsupported mode
        stmt.execute("SELECT * FROM st_test_data");
        try {
          stmt.getMoreResults(Statement.KEEP_CURRENT_RESULT);
          fail("Expected SQLException for KEEP_CURRENT_RESULT");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test warnings
        assertNull(stmt.getWarnings());
        stmt.clearWarnings(); // No-op, should not throw

        // Test result set type and concurrency
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, stmt.getResultSetType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, stmt.getResultSetConcurrency());
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, stmt.getResultSetHoldability());

        // Test closed statement
        stmt.close();
        try {
          stmt.executeQuery("SELECT * FROM st_test_data");
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }

        // Test unwrap
        stmt = new L4St(rq);
        assertTrue(stmt.isWrapperFor(Statement.class));
        assertTrue(stmt.isWrapperFor(Wrapper.class));
        assertFalse(stmt.isWrapperFor(String.class));
        assertSame(stmt, stmt.unwrap(Statement.class));
        try {
          stmt.unwrap(String.class);
          fail("Expected SQLException for invalid unwrap");
        } catch (SQLException e) {
          assertNotNull(e.getMessage());
        }

        stmt.close();
      });

      it("Tests L4St edge cases and invalid inputs", () -> {
        setupTestTable(rq);
        var stmt = new L4St(rq);

        // Test null or empty SQL
        try {
          stmt.executeQuery(null);
          fail("Expected SQLException for null SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          stmt.executeQuery("");
          fail("Expected SQLException for empty SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          stmt.executeUpdate(null);
          fail("Expected SQLException for null SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          stmt.execute(null);
          fail("Expected SQLException for null SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }
        try {
          stmt.addBatch(null);
          fail("Expected SQLException for null SQL");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }

        // Test closed statement state
        stmt.close();
        assertTrue(stmt.isClosed());
        try {
          stmt.getResultSet();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
        try {
          stmt.getUpdateCount();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
        try {
          stmt.getMoreResults();
          fail("Expected SQLException for closed statement");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }

        stmt.close(); // Should be no-op
      });
    }
  }
}
