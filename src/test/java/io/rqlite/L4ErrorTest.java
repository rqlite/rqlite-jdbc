package io.rqlite;

import io.rqlite.client.L4Error;
import io.rqlite.client.L4Options;
import io.rqlite.client.L4Response;
import io.rqlite.client.L4Result;
import io.rqlite.json.Json;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static io.rqlite.jdbc.L4Err.*;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4ErrorTest {

  private static L4Result result(String json) {
    return new L4Result(Json.parse(json).asObject());
  }

  static {
    it("Decodes a legacy error string", () -> {
      var r = result("{\"error\":\"no such table: foo\"}");
      assertNotNull(r.error);
      assertEquals("no such table: foo", r.error.message);
      assertFalse(r.error.isSqlite());
      assertNull(r.error.code);
      assertNull(r.error.extendedCode);
    });

    it("Decodes a structured error_v2 with SQLite codes", () -> {
      var r = result(
        "{\"error_v2\":{\"message\":\"no such table: foo\",\"sqlite\":{\"code\":1,\"extended_code\":1}}}"
      );
      assertNotNull(r.error);
      assertEquals("no such table: foo", r.error.message);
      assertTrue(r.error.isSqlite());
      assertEquals(Integer.valueOf(1), r.error.code);
      assertEquals(Integer.valueOf(1), r.error.extendedCode);
    });

    it("Prefers error_v2 over the legacy error when both are present", () -> {
      var r = result(
        "{\"error\":\"legacy text\",\"error_v2\":{\"message\":\"structured text\","
          + "\"sqlite\":{\"code\":19,\"extended_code\":2067,\"system_errno\":0}}}"
      );
      assertNotNull(r.error);
      assertEquals("structured text", r.error.message);
      assertEquals(Integer.valueOf(19), r.error.code);
      assertEquals(Integer.valueOf(2067), r.error.extendedCode);
      assertEquals(Integer.valueOf(0), r.error.systemErrno);
    });

    it("Decodes a structured error_v2 without SQLite codes", () -> {
      var r = result("{\"error_v2\":{\"message\":\"query timeout\"}}");
      assertNotNull(r.error);
      assertEquals("query timeout", r.error.message);
      assertFalse(r.error.isSqlite());
      assertNull(r.error.code);
    });

    it("Decodes errors across a results array", () -> {
      var json = Json.parse(
        "{\"results\":[{\"last_insert_id\":1,\"rows_affected\":1},"
          + "{\"error_v2\":{\"message\":\"no such table: foo\",\"sqlite\":{\"code\":1,\"extended_code\":1}}}]}"
      ).asObject();
      var res = L4Response.response(200, json);
      assertEquals(2, res.results.size());
      assertNull(res.results.get(0).error);
      assertNotNull(res.results.get(1).error);
      assertEquals(Integer.valueOf(1), res.results.get(1).error.code);
    });

    it("Omits the errors query parameter by default", () -> {
      var params = new L4Options().queryParams(false);
      assertFalse(params.contains("errors="));
    });

    it("Emits the configured errors query parameter", () -> {
      var p = new Properties();
      p.setProperty(L4Options.kErrors, "v2");
      var options = new L4Options(p);
      assertEquals("v2", options.errors);
      assertTrue(options.queryParams(false).contains("errors=v2"));
    });

    it("Rejects an invalid errors format", () -> {
      var p = new Properties();
      p.setProperty(L4Options.kErrors, "v3");
      try {
        new L4Options(p);
        fail("Expected an exception for an invalid errors format");
      } catch (IllegalStateException e) {
        assertNotNull(e.getCause());
      }
    });

    it("Maps SQLite result codes to SQLState and vendor code", () -> {
      var constraint = new L4Error();
      constraint.message = "unique constraint failed";
      constraint.code = 19;
      constraint.extendedCode = 2067;

      assertEquals(SqlStateIntegrityConstraint, sqlStateOf(constraint));
      assertEquals(2067, vendorCodeOf(constraint));

      var ex = statementError(constraint);
      assertEquals("unique constraint failed", ex.getMessage());
      assertEquals(SqlStateIntegrityConstraint, ex.getSQLState());
      assertEquals(2067, ex.getErrorCode());

      var generic = new L4Error();
      generic.message = "incomplete input";
      generic.code = 1;
      generic.extendedCode = 1;
      assertEquals(SqlStateGeneralError, sqlStateOf(generic));
      assertEquals(1, vendorCodeOf(generic));

      var notSqlite = new L4Error();
      notSqlite.message = "query timeout";
      assertEquals(SqlStateGeneralError, sqlStateOf(notSqlite));
      assertEquals(0, vendorCodeOf(notSqlite));

      assertEquals(SqlStateGeneralError, sqlStateOf(null));
      assertEquals(0, vendorCodeOf(null));
    });

    it("Falls back to the primary code when no extended code is present", () -> {
      var busy = new L4Error();
      busy.message = "database is locked";
      busy.code = 5;
      assertEquals(SqlStateSerializationFailure, sqlStateOf(busy));
      assertEquals(5, vendorCodeOf(busy));

      var auth = new L4Error();
      auth.message = "not authorized";
      auth.code = 23;
      assertEquals(SqlStateInvalidAuthorization, sqlStateOf(auth));
      assertEquals(23, vendorCodeOf(auth));
    });

    it("Maps SQLite errors through the JDBC boundary", () -> {
      if (!L4Tests.runIntegrationTests) {
        return;
      }
      // SQLite result codes are only reported by rqlite v10.6.0 and later; older servers send
      // only the legacy error string, so the driver falls back to HY000 / code 0.
      var v2 = L4Tests.supportsErrorV2();
      try (var conn = DriverManager.getConnection(L4Tests.rqUrl + "?errors=v2");
           var stmt = conn.createStatement()) {
        stmt.executeUpdate("DROP TABLE IF EXISTS err_map");
        stmt.executeUpdate("CREATE TABLE err_map (id INTEGER PRIMARY KEY, name TEXT UNIQUE)");
        stmt.executeUpdate("INSERT INTO err_map (id, name) VALUES (1, 'a')");

        // Missing table: SQLITE_ERROR (1) -> HY000; vendor code 1 only with error_v2.
        try {
          stmt.executeQuery("SELECT * FROM no_such_table");
          fail("Expected SQLException for a missing table");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
          assertEquals(v2 ? 1 : 0, e.getErrorCode());
        }

        // UNIQUE violation: SQLITE_CONSTRAINT_UNIQUE (2067) -> 23000 / 2067 only with error_v2.
        try {
          stmt.executeUpdate("INSERT INTO err_map (id, name) VALUES (2, 'a')");
          fail("Expected SQLException for a unique violation");
        } catch (SQLException e) {
          assertEquals(v2 ? SqlStateIntegrityConstraint : SqlStateGeneralError, e.getSQLState());
          assertEquals(v2 ? 2067 : 0, e.getErrorCode());
        }

        stmt.executeUpdate("DROP TABLE err_map");
      }
    });
  }

}
