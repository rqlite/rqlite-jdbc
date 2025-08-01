package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Statement;
import io.rqlite.jdbc.L4Ps;
import io.rqlite.jdbc.L4Rs;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.util.*;

import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Err.*;
import static j8spec.J8Spec.*;
import static java.lang.String.join;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4RsTest {

  private static final L4Client rq = L4Tests.localClient();

  // Helper methods to read streams and readers
  private static byte[] readStream(InputStream is) throws Exception {
    if (is == null) return null;
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = is.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      return baos.toByteArray();
    }
  }

  private static String readReader(Reader reader) throws Exception {
    if (reader == null) return null;
    try (StringWriter sw = new StringWriter()) {
      char[] buffer = new char[1024];
      int len;
      while ((len = reader.read(buffer)) != -1) {
        sw.write(buffer, 0, len);
      }
      return sw.toString();
    }
  }

  static {
    if (L4Tests.runIntegrationTests) {
      it("Validates L4Rs against a live rqlite instance", () -> {
        var stmt = new L4Ps(rq, "SELECT 1");

        var dr = rq.executeSingle("DROP TABLE IF EXISTS rs_test_data");
        assertEquals(200, dr.statusCode);

        // Create table with diverse data types
        var createTable = join("\n", "",
          "CREATE TABLE rs_test_data (",
          "  id INTEGER PRIMARY KEY AUTOINCREMENT,",
          "  num_val NUMERIC,",
          "  bool_val BOOLEAN,", // For BOOLEAN (0/1)
          "  tiny_val TINYINT,", // For TINYINT
          "  small_val SMALLINT,", // For SMALLINT
          "  int_val INTEGER,", // For INTEGER
          "  big_val BIGINT,", // For BIGINT
          "  float_val FLOAT,", // For FLOAT
          "  double_val DOUBLE,", // For DOUBLE
          "  text_val VARCHAR,", // For VARCHAR
          "  date_val DATE,", // For DATE (ISO format)
          "  time_val TIME,", // For TIME (ISO format)
          "  ts_val TIMESTAMP,", // For TIMESTAMP (ISO format)
          "  url_val DATALINK,", // For DATALINK
          "  clob_val CLOB,", // For CLOB
          "  nclob_val NCLOB,", // For NCLOB
          "  nstring_val NVARCHAR,", // For NVARCHAR
          "  blob_val BLOB", // For BLOB
          ")"
        );
        var res0 = rq.executeSingle(createTable);
        assertEquals(200, res0.statusCode);

        var resP = rq.querySingle("PRAGMA table_info(rs_test_data)");
        for (var result : resP.results) {
          System.out.println(result.columns);
          System.out.println(result.types);
          for (var row : result.values) {
            System.out.println(row);
          }
        }

        // Insert test data: valid values and NULLs
        var insertSql = join("", "",
          "INSERT INTO rs_test_data (",
          "  num_val, bool_val, tiny_val, small_val, int_val, big_val, float_val, double_val,",
          "  text_val, date_val, time_val, ts_val, url_val, clob_val, nclob_val, nstring_val, blob_val",
          ") VALUES (",
          "  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
          ")"
        );
        var blobData = Base64.getEncoder().encodeToString("Hello, rqlite!".getBytes(StandardCharsets.UTF_8));
        var res2 = rq.execute(
          true,
          new L4Statement().sql(insertSql).withPositionalParams(
            123.45, // num_val
            1, // bool_val (BOOLEAN: true)
            127, // tiny_val (TINYINT)
            32767, // small_val (SMALLINT)
            2147483647, // int_val (INTEGER)
            9223372036854775807L, // big_val (BIGINT)
            3.14f, // float_val (FLOAT)
            2.71828, // double_val (DOUBLE)
            "Hello, world!", // text_val (VARCHAR)
            "2023-10-15", // date_val (DATE)
            "14:30:00", // time_val (TIME)
            "2023-10-15 14:30:00", // ts_val (TIMESTAMP)
            "https://example.com", // url_val (DATALINK)
            "This is a CLOB", // clob_val (CLOB)
            "This is an NCLOB", // nclob_val (NCLOB)
            "This is an NSTRING", // nstring_val (NVARCHAR)
            blobData // blob_val (BLOB)
          ),
          new L4Statement().sql(insertSql).withPositionalParams(
            0.0, // num_val
            0, // bool_val (BOOLEAN: false)
            -128, // tiny_val
            -128, // small_val
            -2147483648, // int_val
            -9223372036854775808L, // big_val
            -3.14f, // float_val
            -2.71828, // double_val
            null, // text_val (NULL)
            null, // date_val
            null, // time_val
            null, // ts_val
            null, // url_val
            null, // clob_val
            null, // nclob_val
            null, // nstring_val
            null // blob_val
          )
        );
        assertEquals(200, res2.statusCode);

        // Query data and test L4Rs
        var res3 = rq.querySingle("SELECT * FROM rs_test_data");
        assertEquals(200, res3.statusCode);
        var result = res3.results.get(0);
        var rs = new L4Rs(result, stmt);

        var meta = rs.getMetaData();

        for (var col : result.columns) {
          var idx = rs.findColumn(col);
          System.out.printf(
            "typename: %s, r: %s, w: %s, dw: %s%n",
            meta.getColumnTypeName(idx), meta.isReadOnly(idx),
            meta.isWritable(idx), meta.isDefinitelyWritable(idx)
          );          System.out.printf(
            "type: %d, displaySize: %d, class: %s, signed: %s, precision: %d%n",
            meta.getColumnType(idx), meta.getColumnDisplaySize(idx),
            meta.getColumnClassName(idx), meta.isSigned(idx), meta.getPrecision(idx)
          );
          assertFalse(meta.isAutoIncrement(idx));
          assertFalse(meta.isCaseSensitive(idx));
          assertTrue(meta.isSearchable(idx));
          assertFalse(meta.isCurrency(idx));
          assertEquals(ResultSetMetaData.columnNullableUnknown, meta.isNullable(idx));
          assertEquals(col, meta.getColumnLabel(idx));
          assertEquals(col, meta.getColumnName(idx));
          assertTrue(meta.getSchemaName(idx).isEmpty());
          assertTrue(meta.getTableName(idx).isEmpty());
          assertTrue(meta.getCatalogName(idx).isEmpty());
        }

        // Validate column types
        assertEquals(Types.INTEGER, meta.getColumnType(1)); // id (INTEGER)
        assertEquals(Types.NUMERIC, meta.getColumnType(2)); // num_val (NUMERIC)
        assertEquals(Types.BOOLEAN, meta.getColumnType(3)); // bool_val (BOOLEAN)
        assertEquals(Types.TINYINT, meta.getColumnType(4)); // tiny_val (TINYINT)
        assertEquals(Types.SMALLINT, meta.getColumnType(5)); // small_val (SMALLINT)
        assertEquals(Types.INTEGER, meta.getColumnType(6)); // int_val (INTEGER)
        assertEquals(Types.BIGINT, meta.getColumnType(7)); // big_val (BIGINT)
        assertEquals(Types.FLOAT, meta.getColumnType(8)); // float_val (FLOAT)
        assertEquals(Types.DOUBLE, meta.getColumnType(9)); // double_val (DOUBLE)
        assertEquals(Types.VARCHAR, meta.getColumnType(10)); // text_val (VARCHAR)
        assertEquals(Types.DATE, meta.getColumnType(11)); // date_val (DATE)
        assertEquals(Types.TIME, meta.getColumnType(12)); // time_val (TIME)
        assertEquals(Types.TIMESTAMP, meta.getColumnType(13)); // ts_val (TIMESTAMP)
        assertEquals(Types.DATALINK, meta.getColumnType(14)); // url_val (DATALINK)
        assertEquals(Types.CLOB, meta.getColumnType(15)); // clob_val (CLOB)
        assertEquals(Types.NCLOB, meta.getColumnType(16)); // nclob_val (NCLOB)
        assertEquals(Types.NVARCHAR, meta.getColumnType(17)); // nstring_val (NVARCHAR)
        assertEquals(Types.BLOB, meta.getColumnType(18)); // blob_val (BLOB)

        // Validate metadata for selected columns
        assertEquals(Integer.class.getCanonicalName(), meta.getColumnClassName(1)); // id (INTEGER)
        assertEquals(java.math.BigDecimal.class.getCanonicalName(), meta.getColumnClassName(2)); // num_val (NUMERIC)
        assertEquals(Boolean.class.getCanonicalName(), meta.getColumnClassName(3)); // bool_val (BOOLEAN)
        assertEquals(Float.class.getCanonicalName(), meta.getColumnClassName(8)); // float_val (FLOAT)
        assertEquals(String.class.getCanonicalName(), meta.getColumnClassName(10)); // text_val (VARCHAR)

        assertEquals(10, meta.getPrecision(1)); // id (INTEGER)
        assertEquals(38, meta.getPrecision(2)); // num_val (NUMERIC)
        assertEquals(1, meta.getPrecision(3)); // bool_val (BOOLEAN)
        assertEquals(7, meta.getPrecision(8)); // float_val (FLOAT)
        assertEquals(255, meta.getPrecision(10)); // text_val (VARCHAR)

        assertEquals(11, meta.getColumnDisplaySize(1)); // id (INTEGER)
        assertEquals(38, meta.getColumnDisplaySize(2)); // num_val (NUMERIC)
        assertEquals(5, meta.getColumnDisplaySize(3)); // bool_val (BOOLEAN)
        assertEquals(25, meta.getColumnDisplaySize(8)); // float_val (FLOAT)
        assertEquals(255, meta.getColumnDisplaySize(10)); // text_val (VARCHAR)

        // Test row 1: Valid values
        assertTrue("Expected first row", rs.next());
        assertEquals(1, rs.getInt("id"));
        assertTrue("Expected true for bool_val", rs.getBoolean("bool_val"));
        assertEquals(127, rs.getByte("tiny_val"));
        assertEquals(32767, rs.getShort("small_val"));
        assertEquals(2147483647, rs.getInt("int_val"));
        assertEquals(9223372036854775807L, rs.getLong("big_val"));
        assertEquals(3.14f, rs.getFloat("float_val"), 0.001f);
        assertEquals(2.71828, rs.getDouble("double_val"), 0.001);
        assertEquals("Hello, world!", rs.getString("text_val"));

        // Normalize expected date to UTC and debug
        var utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")); // UTC Calendar
        var localDate = LocalDate.of(2023, 10, 15);
        var zdt = localDate.atStartOfDay(ZoneId.of("UTC"));
        var expectedDate = new Date(zdt.toInstant().toEpochMilli());
        var actualDate = rs.getDate("date_val", utcCalendar);
        assertEquals(expectedDate.getTime(), actualDate.getTime());
        assertEquals(expectedDate, actualDate);

        assertEquals(Time.valueOf("14:30:00"), rs.getTime("time_val"));

        // Normalize expected timestamp to UTC
        var expectedTsLocal = LocalDateTime.of(2023, 10, 15, 14, 30, 0);
        var expectedTsZdt = expectedTsLocal.atZone(ZoneId.of("UTC"));
        var expectedTs = new Timestamp(expectedTsZdt.toInstant().toEpochMilli());
        var actualTs = rs.getTimestamp("ts_val", utcCalendar); // Use utcCalendar
        System.out.println("Expected TS millis: " + expectedTs.getTime() + " (" + expectedTs + ")");
        System.out.println("Actual TS millis: " + actualTs.getTime() + " (" + actualTs + ")");
        assertEquals(expectedTs, actualTs);

        assertEquals(new URL("https://example.com"), rs.getURL("url_val"));

        assertEquals("This is a CLOB", rs.getClob("clob_val").getSubString(1, 14));
        assertEquals("This is an NCLOB", rs.getNClob("nclob_val").getSubString(1, 16));
        assertEquals("This is an NSTRING", rs.getNString("nstring_val"));
        assertEquals("Hello, rqlite!", new String(rs.getBytes("blob_val"), StandardCharsets.UTF_8));
        assertEquals("Hello, world!", rs.getObject("text_val", String.class));

        // Test BigDecimal scale handling for num_val (NUMERIC)
        assertEquals(new BigDecimal("3.14"), rs.getBigDecimal("float_val", 2)); // Specify scale
        assertEquals(new BigDecimal("123"), rs.getBigDecimal("num_val", 0)); // Scale 0: 123
        assertEquals(new BigDecimal("123.45"), rs.getBigDecimal("num_val", 2)); // Scale 2: 123.45
        assertEquals(new BigDecimal("123.4500"), rs.getBigDecimal("num_val", 4)); // Scale 4: 123.4500
        assertEquals(new BigDecimal("123.45"), rs.getBigDecimal("num_val"));

        assertEquals("This is a CLOB", new String(readStream(rs.getAsciiStream("clob_val")), StandardCharsets.US_ASCII));
        assertEquals("This is a CLOB", new String(readStream(rs.getUnicodeStream("clob_val")), StandardCharsets.UTF_16BE));
        assertEquals("This is a CLOB", new String(readStream(rs.getBinaryStream("clob_val")), StandardCharsets.UTF_8));
        assertEquals("This is a CLOB", readReader(rs.getCharacterStream("clob_val")));
        assertEquals("This is an NSTRING", readReader(rs.getNCharacterStream("nstring_val")));
        assertEquals("Hello, rqlite!", new String(readStream(rs.getBinaryStream("blob_val")), StandardCharsets.UTF_8));

        var blob = rs.getBlob("blob_val");
        var str = new String(blob.getBytes(1, (int) blob.length()));
        assertEquals("Hello, rqlite!", str);

        var obj = rs.getObject("blob_val", new HashMap<>());
        assertNotNull(obj);

        // Test row 2: NULL values
        assertTrue("Expected second row", rs.next());
        assertEquals(2, rs.getInt("id"));
        assertFalse("Expected false for bool_val", rs.getBoolean("bool_val"));
        assertEquals(-128, rs.getByte("tiny_val"));
        assertEquals(-128, rs.getShort("small_val"));
        assertEquals(-2147483648, rs.getInt("int_val"));
        assertEquals(-9223372036854775808L, rs.getLong("big_val"));
        assertEquals(-3.14f, rs.getFloat("float_val"), 0.001f);
        assertEquals(-2.71828, rs.getDouble("double_val"), 0.001);
        assertNull(rs.getString("text_val"));
        assertTrue("Expected wasNull for text_val", rs.wasNull());
        assertNull(rs.getDate("date_val"));
        assertTrue("Expected wasNull for date_val", rs.wasNull());
        assertNull(rs.getTime("time_val"));
        assertTrue("Expected wasNull for time_val", rs.wasNull());
        assertNull(rs.getTimestamp("ts_val"));
        assertTrue("Expected wasNull for ts_val", rs.wasNull());
        assertNull(rs.getURL("url_val"));
        assertTrue("Expected wasNull for url_val", rs.wasNull());
        assertNull(rs.getClob("clob_val"));
        assertTrue("Expected wasNull for clob_val", rs.wasNull());
        assertNull(rs.getNClob("nclob_val"));
        assertTrue("Expected wasNull for nclob_val", rs.wasNull());
        assertNull(rs.getNString("nstring_val"));
        assertTrue("Expected wasNull for nstring_val", rs.wasNull());
        assertNull(rs.getBytes("blob_val"));
        assertTrue("Expected wasNull for blob_val", rs.wasNull());
        assertNull(rs.getObject("text_val", String.class));
        assertTrue("Expected wasNull for text_val", rs.wasNull());
        assertNull(rs.getAsciiStream("clob_val"));
        assertTrue("Expected wasNull for ASCII stream", rs.wasNull());
        assertNull(rs.getUnicodeStream("clob_val"));
        assertTrue("Expected wasNull for Unicode stream", rs.wasNull());
        assertNull(rs.getBinaryStream("clob_val"));
        assertTrue("Expected wasNull for binary stream", rs.wasNull());
        assertNull(rs.getCharacterStream("clob_val"));
        assertTrue("Expected wasNull for character stream", rs.wasNull());
        assertNull(rs.getNCharacterStream("nstring_val"));
        assertTrue("Expected wasNull for N-character stream", rs.wasNull());

        try {
          rs.getSQLXML("text_val");
          fail("Expected SQLException for getSQLXML");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.getString("invalid_col");
          fail("Expected SQLException for invalid column label");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidColumn, e.getSQLState());
        }

        result.types.set(0, "UNKNOWN");
        try {
          rs.getMetaData().getColumnType(1);
          fail("Expected SQLException");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidType, e.getSQLState());
        }

        // Test NULL type
        result.types.set(0, RQ_NULL);
        assertEquals(Types.NULL, rs.getMetaData().getColumnType(1));

        // Test no more rows
        assertFalse("Expected no more rows", rs.next());

        // Test closed ResultSet
        rs.close();
        try {
          rs.getString("text_val");
          fail("Expected SQLException for closed ResultSet");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
      });

      // New test block: Test ResultSet navigation and state
      it("Tests L4Rs navigation and state methods", () -> {
        var stmt = new L4Ps(rq, "SELECT 1");
        var res3 = rq.querySingle("SELECT * FROM rs_test_data");
        assertEquals(200, res3.statusCode);
        var result = res3.results.get(0);
        var rs = new L4Rs(result, stmt);

        // Test initial state
        assertTrue(rs.isBeforeFirst());
        assertFalse(rs.isAfterLast());
        assertFalse(rs.isFirst());
        assertFalse(rs.isLast());
        assertEquals(0, rs.getRow());

        // Move to first row
        assertTrue(rs.next());
        assertFalse(rs.isBeforeFirst());
        assertFalse(rs.isAfterLast());
        assertTrue(rs.isFirst());
        assertFalse(rs.isLast());
        assertEquals(1, rs.getRow());

        // Move to second row
        assertTrue(rs.next());
        assertFalse(rs.isBeforeFirst());
        assertFalse(rs.isAfterLast());
        assertFalse(rs.isFirst());
        assertTrue(rs.isLast());
        assertEquals(2, rs.getRow());

        // Move past last row
        assertFalse(rs.next());
        assertFalse(rs.isBeforeFirst());
        assertTrue(rs.isAfterLast());
        assertFalse(rs.isFirst());
        assertFalse(rs.isLast());
        assertEquals(0, rs.getRow());

        // Test closed state
        rs.close();
        assertTrue(rs.isClosed());
        try {
          rs.next();
          fail("Expected SQLException for closed ResultSet");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
      });

      it("Tests L4Rs unsupported operations and error handling", () -> {
        var stmt = new L4Ps(rq, "SELECT 1");
        var res3 = rq.querySingle("SELECT * FROM rs_test_data");
        assertEquals(200, res3.statusCode);
        var result = res3.results.get(0);
        var rs = new L4Rs(result, stmt);

        assertTrue(rs.next());

        // Test unsupported navigation
        try {
          rs.beforeFirst();
          fail("Expected SQLException for beforeFirst");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.afterLast();
          fail("Expected SQLException for afterLast");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.first();
          fail("Expected SQLException for first");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.last();
          fail("Expected SQLException for last");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.absolute(1);
          fail("Expected SQLException for absolute");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.relative(1);
          fail("Expected SQLException for relative");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.previous();
          fail("Expected SQLException for previous");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test fetch direction and size
        rs.setFetchDirection(ResultSet.FETCH_FORWARD); // Should succeed
        assertEquals(ResultSet.FETCH_FORWARD, rs.getFetchDirection());
        try {
          rs.setFetchDirection(ResultSet.FETCH_REVERSE);
          fail("Expected SQLException for FETCH_REVERSE");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        rs.setFetchSize(10); // Should succeed
        assertEquals(0, rs.getFetchSize()); // Always returns 0
        try {
          rs.setFetchSize(-1);
          fail("Expected SQLException for negative fetch size");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidAttr, e.getSQLState());
        }

        // Test ResultSet type and concurrency
        assertEquals(ResultSet.TYPE_FORWARD_ONLY, rs.getType());
        assertEquals(ResultSet.CONCUR_READ_ONLY, rs.getConcurrency());

        // Test row update/insert/delete
        assertFalse(rs.rowUpdated());
        assertFalse(rs.rowInserted());
        assertFalse(rs.rowDeleted());
        try {
          rs.updateString("text_val", "Updated");
          fail("Expected SQLException for updateString");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.insertRow();
          fail("Expected SQLException for insertRow");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.deleteRow();
          fail("Expected SQLException for deleteRow");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test unsupported types
        try {
          rs.getRef("id");
          fail("Expected SQLException for getRef");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.getArray("text_val");
          fail("Expected SQLException for getArray");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }
        try {
          rs.getRowId("id");
          fail("Expected SQLException for getRowId");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test cursor name
        try {
          rs.getCursorName();
          fail("Expected SQLException for getCursorName");
        } catch (SQLException e) {
          assertEquals(SqlStateFeatureNotSupported, e.getSQLState());
        }

        // Test warnings
        assertNull(rs.getWarnings());
        rs.clearWarnings(); // No-op, should not throw

        // Test statement
        assertEquals(stmt, rs.getStatement());

        // Test unwrap
        assertTrue(rs.isWrapperFor(ResultSet.class));
        assertTrue(rs.isWrapperFor(Wrapper.class));
        assertFalse(rs.isWrapperFor(String.class));
        assertSame(rs, rs.unwrap(ResultSet.class));
        try {
          rs.unwrap(String.class);
          fail("Expected SQLException for invalid unwrap");
        } catch (SQLException e) {
          assertNotNull(e.getMessage());
        }

        rs.close();
      });

      it("Tests L4Rs with empty ResultSet", () -> {
        var stmt = new L4Ps(rq, "SELECT 1");
        var res = rq.querySingle("SELECT * FROM rs_test_data WHERE id = 999");
        assertEquals(200, res.statusCode);
        var result = res.results.get(0);
        var rs = new L4Rs(result, stmt);

        // Test empty ResultSet
        assertFalse(rs.isBeforeFirst());
        assertFalse(rs.isAfterLast());
        assertFalse(rs.isFirst());
        assertFalse(rs.isLast());
        assertEquals(0, rs.getRow());
        assertFalse(rs.next());

        // Test metadata
        var meta = rs.getMetaData();
        assertEquals(18, meta.getColumnCount());
        assertEquals(Types.INTEGER, meta.getColumnType(1));

        rs.close();
        try {
          rs.getInt("id");
          fail("Expected SQLException for closed ResultSet");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
      });

      it("Tests L4Rs with invalid column index and edge cases", () -> {
        var stmt = new L4Ps(rq, "SELECT 1");
        var res3 = rq.querySingle("SELECT * FROM rs_test_data");
        assertEquals(200, res3.statusCode);
        var result = res3.results.get(0);
        var rs = new L4Rs(result, stmt);

        assertTrue(rs.next());

        // Test invalid column index
        try {
          rs.getString(0);
          fail("Expected SQLException for invalid column index");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidColumn, e.getSQLState());
        }
        try {
          rs.getString(19);
          fail("Expected SQLException for invalid column index");
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidColumn, e.getSQLState());
        }

        // Test invalid row access
        rs.close();
        try {
          rs.getString(1);
          fail("Expected SQLException for closed ResultSet");
        } catch (SQLException e) {
          assertEquals(SqlStateGeneralError, e.getSQLState());
        }
      });
    }
  }

}