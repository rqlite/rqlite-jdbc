package io.rqlite;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.sql.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4ChinookTest {

  static {
    if (L4Tests.runIntegrationTests) {
      beforeAll(() -> ChinookFixture.load(L4Tests.localClient()));

      it("Runs joins and aggregates over the reference schema", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          try (var rs = stmt.executeQuery(
            "SELECT ar.Name AS artist, al.Title AS album, t.Name AS track " +
            "FROM Track t JOIN Album al ON t.AlbumId = al.AlbumId " +
            "JOIN Artist ar ON al.ArtistId = ar.ArtistId ORDER BY t.TrackId")) {
            assertTrue(rs.next());
            assertEquals("AC/DC", rs.getString("artist"));
            assertEquals("For Those About To Rock We Salute You", rs.getString("album"));
            assertTrue(rs.next());
            assertEquals("Accept", rs.getString("artist"));
          }

          try (var rs = stmt.executeQuery("SELECT COUNT(*) AS c, SUM(Quantity) AS q FROM InvoiceLine")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt("c"));
            assertEquals(6, rs.getInt("q"));
          }

          try (var rs = stmt.executeQuery(
            "SELECT al.ArtistId AS aid, COUNT(*) AS n FROM Track t " +
            "JOIN Album al ON t.AlbumId = al.AlbumId " +
            "GROUP BY al.ArtistId HAVING COUNT(*) >= 1 ORDER BY al.ArtistId")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("aid"));
          }
        }
      });

      it("Round-trips types, NULLs and dates", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          try (var rs = stmt.executeQuery("SELECT Composer, Bytes, UnitPrice FROM Track WHERE TrackId = 5")) {
            assertTrue(rs.next());
            assertNull(rs.getString("Composer"));
            assertTrue(rs.wasNull());
            assertNull(rs.getObject("Bytes"));
            assertTrue(rs.wasNull());
            assertEquals(0.99, rs.getDouble("UnitPrice"), 0.001);
          }

          try (var rs = stmt.executeQuery("SELECT InvoiceDate FROM Invoice WHERE InvoiceId = 1")) {
            assertTrue(rs.next());
            assertNotNull(rs.getTimestamp("InvoiceDate"));
          }

          // Subquery + LIMIT/OFFSET
          try (var rs = stmt.executeQuery(
            "SELECT Name FROM Track WHERE TrackId IN " +
            "(SELECT TrackId FROM InvoiceLine) ORDER BY TrackId LIMIT 2 OFFSET 1")) {
            assertTrue(rs.next());
          }
        }
      });

      it("Supports composite-key inserts and deletes", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          try (var ps = conn.prepareStatement("INSERT INTO PlaylistTrack (PlaylistId, TrackId) VALUES (?, ?)")) {
            ps.setInt(1, 2);
            ps.setInt(2, 2);
            assertEquals(1, ps.executeUpdate());
          }
          try (var rs = conn.createStatement().executeQuery(
            "SELECT COUNT(*) AS c FROM PlaylistTrack WHERE PlaylistId = 2 AND TrackId = 2")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("c"));
          }
          conn.createStatement().executeUpdate("DELETE FROM PlaylistTrack WHERE PlaylistId = 2 AND TrackId = 2");
        }
      });

      it("Exposes generated keys and supports rollback", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          try (var ps = conn.prepareStatement(
            "INSERT INTO Genre (GenreId, Name) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, 99);
            ps.setString(2, "Test Genre");
            assertEquals(1, ps.executeUpdate());
            try (var keys = ps.getGeneratedKeys()) {
              assertTrue(keys.next());
              assertTrue(keys.getLong(1) > 0);
            }
          }
          conn.createStatement().executeUpdate("DELETE FROM Genre WHERE GenreId = 99");

          conn.setAutoCommit(false);
          conn.createStatement().executeUpdate("INSERT INTO Genre (GenreId, Name) VALUES (100, 'Rolled Back')");
          conn.rollback();
          conn.setAutoCommit(true);
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM Genre WHERE GenreId = 100")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("c"));
          }
        }
      });

      it("Executes batches atomically", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          stmt.addBatch("INSERT INTO Genre (GenreId, Name) VALUES (200, 'Batch A')");
          stmt.addBatch("INSERT INTO Genre (GenreId, Name) VALUES (201, 'Batch B')");
          assertArrayEquals(new int[]{1, 1}, stmt.executeBatch());
          stmt.executeUpdate("DELETE FROM Genre WHERE GenreId IN (200, 201)");
        }
      });

      it("Reports metadata for the reference schema", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          var meta = conn.getMetaData();

          var tables = new HashSet<String>();
          try (var rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
              tables.add(rs.getString("TABLE_NAME"));
            }
          }
          assertTrue(tables.containsAll(
            List.of("Album", "Artist", "Track", "InvoiceLine", "PlaylistTrack")));

          try (var rs = meta.getPrimaryKeys(null, null, "PlaylistTrack")) {
            var cols = new HashSet<String>();
            while (rs.next()) {
              cols.add(rs.getString("COLUMN_NAME"));
            }
            assertEquals(Set.of("PlaylistId", "TrackId"), cols);
          }

          try (var rs = meta.getImportedKeys(null, null, "InvoiceLine")) {
            var pkTables = new HashSet<String>();
            while (rs.next()) {
              pkTables.add(rs.getString("PKTABLE_NAME"));
            }
            assertTrue(pkTables.contains("Invoice"));
            assertTrue(pkTables.contains("Track"));
          }

          try (var rs = meta.getColumns(null, null, "Track", null)) {
            var names = new HashSet<String>();
            while (rs.next()) {
              names.add(rs.getString("COLUMN_NAME"));
            }
            assertTrue(names.contains("TrackId"));
            assertTrue(names.contains("UnitPrice"));
          }
        }
      });

      it("Splits multi-statement SQL through Statement.execute", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          // Two SELECTs -> two result sets.
          assertTrue(stmt.execute(
            "SELECT Title FROM Album WHERE AlbumId = 1; SELECT Name FROM Artist WHERE ArtistId = 1"));
          try (var rs = stmt.getResultSet()) {
            assertTrue(rs.next());
            assertEquals("For Those About To Rock We Salute You", rs.getString(1));
          }
          assertTrue(stmt.getMoreResults());
          try (var rs = stmt.getResultSet()) {
            assertTrue(rs.next());
            assertEquals("AC/DC", rs.getString(1));
          }
          assertFalse(stmt.getMoreResults());

          // Semicolons inside a string literal, a line comment, a block comment and
          // quoted identifiers must not split statements.
          var tricky = "SELECT 'a;b' AS v FROM Album WHERE AlbumId = 1; "
            + "SELECT COUNT(*) AS c FROM Album -- ; not a separator\n; "
            + "SELECT COUNT(*) AS c2 FROM [Album] /* ; still not */ WHERE AlbumId = 1";
          var statements = 0;
          assertTrue(stmt.execute(tricky));
          statements++;
          while (stmt.getMoreResults()) {
            statements++;
          }
          assertEquals(3, statements);
        }
      });

      it("Routes statements containing SELECT substrings correctly", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          // INSERT ... SELECT contains SELECT but is a write.
          assertFalse(stmt.execute("INSERT INTO Album (AlbumId, Title, ArtistId) SELECT 900, 'Copy', 1"));
          assertEquals(1, stmt.getUpdateCount());
          stmt.executeUpdate("DELETE FROM Album WHERE AlbumId = 900");

          // Leading comments containing the word SELECT must not change routing.
          assertFalse(stmt.execute("/* SELECT */ INSERT INTO Genre (GenreId, Name) VALUES (301, 'Z')"));
          stmt.executeUpdate("DELETE FROM Genre WHERE GenreId = 301");
          assertFalse(stmt.execute("-- SELECT\nINSERT INTO Genre (GenreId, Name) VALUES (302, 'Z2')"));
          stmt.executeUpdate("DELETE FROM Genre WHERE GenreId = 302");

          // CTEs, EXPLAIN, PRAGMA and VALUES are reads.
          assertTrue(stmt.execute("WITH c AS (SELECT AlbumId FROM Album) SELECT COUNT(*) AS n FROM c"));
          assertTrue(stmt.execute("EXPLAIN QUERY PLAN SELECT * FROM Album"));
          assertTrue(stmt.execute("PRAGMA table_info('Track')"));
          assertTrue(stmt.execute("VALUES (1), (2)"));
        }
      });

      it("Handles quoted identifiers and parser-significant literals", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          try (var rs = stmt.executeQuery("SELECT [Album].[Title] FROM [Album] WHERE [AlbumId] = 1")) {
            assertTrue(rs.next());
            assertEquals("For Those About To Rock We Salute You", rs.getString(1));
          }
          try (var rs = stmt.executeQuery("SELECT \"Album\".\"Title\" FROM \"Album\" WHERE \"AlbumId\" = 1")) {
            assertTrue(rs.next());
            assertEquals("For Those About To Rock We Salute You", rs.getString(1));
          }
          try (var rs = stmt.executeQuery("SELECT `Album`.`Title` FROM `Album` WHERE `AlbumId` = 1")) {
            assertTrue(rs.next());
            assertEquals("For Those About To Rock We Salute You", rs.getString(1));
          }
          try (var rs = stmt.executeQuery(
            "SELECT 'a;b' AS v, 'it''s' AS w, '--x' AS x, '/*y*/' AS y, 1 AS \"a;b\"")) {
            assertTrue(rs.next());
            assertEquals("a;b", rs.getString("v"));
            assertEquals("it's", rs.getString("w"));
            assertEquals("--x", rs.getString("x"));
            assertEquals("/*y*/", rs.getString("y"));
            assertEquals(1, rs.getInt("a;b"));
          }
          // The literal 'SELECT' does not change executeQuery routing.
          try (var rs = stmt.executeQuery("SELECT 'SELECT' AS x")) {
            assertTrue(rs.next());
            assertEquals("SELECT", rs.getString(1));
          }
        }
      });

      it("Executes recursive CTEs and window functions", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl);
             var stmt = conn.createStatement()) {
          try (var rs = stmt.executeQuery(
            "WITH RECURSIVE emp AS ("
              + " SELECT EmployeeId, ReportsTo FROM Employee WHERE ReportsTo IS NULL"
              + " UNION ALL"
              + " SELECT e.EmployeeId, e.ReportsTo FROM Employee e JOIN emp ON e.ReportsTo = emp.EmployeeId"
              + ") SELECT COUNT(*) AS c FROM emp")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("c"));
          }
          try (var rs = stmt.executeQuery(
            "SELECT EmployeeId, ROW_NUMBER() OVER (ORDER BY EmployeeId) AS rn "
              + "FROM Employee ORDER BY EmployeeId")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("EmployeeId"));
            assertEquals(1, rs.getInt("rn"));
          }
        }
      });

      it("Parses placeholders for prepared statements with tricky SQL", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          // '?' inside a literal is not a placeholder.
          try (var ps = conn.prepareStatement("SELECT * FROM Track WHERE TrackId = ? AND Name <> '?'")) {
            assertEquals(1, ps.getParameterMetaData().getParameterCount());
            ps.setInt(1, 1);
            try (var rs = ps.executeQuery()) {
              assertTrue(rs.next());
              assertEquals("For Those About To Rock (We Salute You)", rs.getString("Name"));
            }
          }
          try (var ps = conn.prepareStatement(
            "SELECT * FROM [PlaylistTrack] WHERE PlaylistId = ? AND TrackId = ?")) {
            assertEquals(2, ps.getParameterMetaData().getParameterCount());
            ps.setInt(1, 1);
            ps.setInt(2, 2);
            try (var rs = ps.executeQuery()) {
              assertTrue(rs.next());
            }
          }
          try (var ps = conn.prepareStatement("INSERT INTO PlaylistTrack (PlaylistId, TrackId) VALUES (?, ?)")) {
            assertEquals(2, ps.getParameterMetaData().getParameterCount());
            ps.setInt(1, 2);
            ps.setInt(2, 2);
            assertEquals(1, ps.executeUpdate());
          }
          conn.createStatement().executeUpdate("DELETE FROM PlaylistTrack WHERE PlaylistId = 2 AND TrackId = 2");

          // Explicit numeric and named placeholders.
          try (var ps = conn.prepareStatement("SELECT * FROM Track WHERE TrackId = ?1")) {
            assertEquals(1, ps.getParameterMetaData().getParameterCount());
          }
          try (var ps = conn.prepareStatement("SELECT * FROM Track WHERE Name = :name")) {
            assertEquals(1, ps.getParameterMetaData().getParameterCount());
          }
        }
      });
    }
  }

}
