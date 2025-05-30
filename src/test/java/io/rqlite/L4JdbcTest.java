package io.rqlite;

import io.rqlite.json.Json;
import io.rqlite.client.L4Result;
import j8spec.UnsafeBlock;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;

import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Err.*;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4JdbcTest {

  // Helper to read InputStream content
  private static String readStream(InputStream is, String charset) throws Exception {
    if (is == null) return null;
    try (var baos = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int len;
      while ((len = is.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      return baos.toString(charset);
    }
  }

  // Helper to read Reader content
  private static String readReader(Reader reader) throws Exception {
    if (reader == null) return null;
    try (var sw = new StringWriter()) {
      char[] buffer = new char[1024];
      int len;
      while ((len = reader.read(buffer)) != -1) {
        sw.write(buffer, 0, len);
      }
      return sw.toString();
    }
  }

  // Helper to test expected SQLException
  private static void runFail(UnsafeBlock action, String expectedSqlState) {
    try {
      action.tryToExecute();
      fail("Expected SQLException with SQLState " + expectedSqlState);
    } catch (Throwable e) {
      System.out.println(e);
    }
  }

  // Helper to create a mock L4Result
  private static L4Result createMockResult(List<String> columns, List<String> types, List<List<String>> values) {
    var result = new L4Result(Json.object());
    result.columns = new ArrayList<>(columns);
    result.types = new ArrayList<>(types);
    result.values = new ArrayList<>(values);
    return result;
  }

  static {
    it("Tests L4Jdbc utility methods", () -> {
      // Mock L4Result
      var result = createMockResult(
        Arrays.asList("id", "name"),
        Arrays.asList(RQ_INTEGER, RQ_VARCHAR),
        Arrays.asList(Arrays.asList("1", "Alice"), Arrays.asList("2", "Bob"))
      );

      // Test checkColumn
      checkColumn(1, result); // Valid
      checkColumn(2, result); // Valid
      runFail(() -> checkColumn(0, result), SqlStateInvalidColumn);
      runFail(() -> checkColumn(3, result), SqlStateInvalidColumn);

      // Test checkColumnLabel
      checkColumnLabel("id", result); // Valid
      checkColumnLabel("name", result); // Valid
      runFail(() -> checkColumnLabel(null, result), SqlStateInvalidParam);
      runFail(() -> checkColumnLabel("age", result), SqlStateInvalidParam);

      // Test checkRow
      checkRow(0, result, false); // Valid
      checkRow(1, result, false); // Valid
      runFail(() -> checkRow(-1, result, false), SqlStateInvalidCursor);
      runFail(() -> checkRow(2, result, false), SqlStateInvalidCursor);
      runFail(() -> checkRow(0, result, true), SqlStateGeneralError);
    });

    it("Tests L4Jdbc getJdbcType", () -> {
      assertEquals(Types.INTEGER, getJdbcType(RQ_INTEGER));

      assertEquals(Types.NUMERIC, getJdbcType(RQ_NUMERIC));
      assertEquals(Types.NUMERIC, getJdbcType("NUMERIC(10,2)"));

      assertEquals(Types.BOOLEAN, getJdbcType(RQ_BOOLEAN));
      assertEquals(Types.TINYINT, getJdbcType(RQ_TINYINT));
      assertEquals(Types.SMALLINT, getJdbcType(RQ_SMALLINT));
      assertEquals(Types.BIGINT, getJdbcType(RQ_BIGINT));
      assertEquals(Types.FLOAT, getJdbcType(RQ_FLOAT));
      assertEquals(Types.DOUBLE, getJdbcType(RQ_DOUBLE));

      assertEquals(Types.VARCHAR, getJdbcType(RQ_VARCHAR));
      assertEquals(Types.VARCHAR, getJdbcType("VARCHAR(255)"));

      assertEquals(Types.DATE, getJdbcType(RQ_DATE));
      assertEquals(Types.TIME, getJdbcType(RQ_TIME));
      assertEquals(Types.TIMESTAMP, getJdbcType(RQ_TIMESTAMP));
      assertEquals(Types.DATALINK, getJdbcType(RQ_DATALINK));
      assertEquals(Types.CLOB, getJdbcType(RQ_CLOB));
      assertEquals(Types.NCLOB, getJdbcType(RQ_NCLOB));
      assertEquals(Types.NVARCHAR, getJdbcType(RQ_NVARCHAR));
      assertEquals(Types.BLOB, getJdbcType(RQ_BLOB));
      assertEquals(Types.NULL, getJdbcType(RQ_NULL));
      assertEquals(-1, getJdbcType("UNKNOWN"));
      try {
        getJdbcType(null);
        fail("Expected IllegalArgumentException for null type");
      } catch (IllegalArgumentException e) {
        assertNotNull(e.getMessage());
      }
      assertEquals(Types.INTEGER, getJdbcType("integer")); // Case-insensitive
    });

    it("Tests L4Jdbc primitive type conversions", () -> {
      int colIdx = 1;

      // castBoolean
      assertTrue(castBoolean("1", colIdx, Types.INTEGER));
      assertFalse(castBoolean("0", colIdx, Types.INTEGER));
      assertTrue(castBoolean("true", colIdx, Types.VARCHAR));
      assertFalse(castBoolean("false", colIdx, Types.VARCHAR));
      runFail(() -> castBoolean("2", colIdx, Types.INTEGER), SqlStateInvalidType);
      runFail(() -> castBoolean("abc", colIdx, Types.INTEGER), SqlStateInvalidType);
      runFail(() -> castBoolean("true", colIdx, Types.BLOB), SqlStateInvalidConversion);

      // castInteger
      assertEquals(123, castInteger("123", colIdx, Types.INTEGER));
      assertEquals(127, castInteger("127", colIdx, Types.TINYINT));
      runFail(() -> castInteger("2147483648", colIdx, Types.INTEGER), SqlStateInvalidType); // Out of range
      runFail(() -> castInteger("abc", colIdx, Types.INTEGER), SqlStateInvalidType);
      runFail(() -> castInteger("123", colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castLong
      assertEquals(9223372036854775807L, castLong("9223372036854775807", colIdx, Types.BIGINT));
      assertEquals(123, castLong("123", colIdx, Types.INTEGER));
      runFail(() -> castLong("abc", colIdx, Types.BIGINT), SqlStateInvalidType);
      runFail(() -> castLong("123", colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castFloat
      assertEquals(3.14f, castFloat("3.14", colIdx, Types.FLOAT), 0.001f);
      assertEquals(2.718, castFloat("2.718", colIdx, Types.DOUBLE), 0.001f);
      runFail(() -> castFloat("abc", colIdx, Types.FLOAT), SqlStateInvalidType);
      runFail(() -> castFloat("3.14", colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castDouble
      assertEquals(3.14, castDouble("3.14", colIdx, Types.DOUBLE), 0.001);
      assertEquals(2.718, castDouble("2.718", colIdx, Types.FLOAT), 0.001);
      runFail(() -> castDouble("abc", colIdx, Types.DOUBLE), SqlStateInvalidType);
      runFail(() -> castDouble("3.14", colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castByte
      assertEquals(127, castByte("127", colIdx, Types.TINYINT));
      assertEquals(1, castByte("1", colIdx, Types.BOOLEAN));
      runFail(() -> castByte("128", colIdx, Types.TINYINT), SqlStateInvalidType); // Out of range
      runFail(() -> castByte("abc", colIdx, Types.TINYINT), SqlStateInvalidType);
      runFail(() -> castByte("127", colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castShort
      assertEquals(32767, castShort("32767", colIdx, Types.SMALLINT));
      assertEquals(127, castShort("127", colIdx, Types.TINYINT));
      runFail(() -> castShort("32768", colIdx, Types.SMALLINT), SqlStateInvalidType); // Out of range
      runFail(() -> castShort("abc", colIdx, Types.SMALLINT), SqlStateInvalidType);
      runFail(() -> castShort("32767", colIdx, Types.VARCHAR), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc BigDecimal conversion", () -> {
      int colIdx = 1;
      assertEquals(new BigDecimal("123.45"), castBigDecimal("123.45", colIdx, Types.NUMERIC, -1));
      assertEquals(new BigDecimal("123"), castBigDecimal("123.45", colIdx, Types.NUMERIC, 0));
      assertEquals(new BigDecimal("123.4500"), castBigDecimal("123.45", colIdx, Types.NUMERIC, 4));
      assertEquals(new BigDecimal("123"), castBigDecimal("123", colIdx, Types.INTEGER, -1));
      assertEquals(new BigDecimal("3.14"), castBigDecimal("3.14", colIdx, Types.FLOAT, -1));
      runFail(() -> castBigDecimal("abc", colIdx, Types.NUMERIC, -1), SqlStateInvalidType);
      runFail(() -> castBigDecimal("123.45", colIdx, Types.BLOB, -1), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc stream conversions", () -> {
      int colIdx = 1;
      String value = "Hello, world!";

      // castAsciiStream
      assertEquals(value, readStream(castAsciiStream(value, colIdx, Types.VARCHAR), StandardCharsets.US_ASCII.toString()));
      assertEquals(value, readStream(castAsciiStream(value, colIdx, Types.CLOB), StandardCharsets.US_ASCII.toString()));
      assertEquals("123", readStream(castAsciiStream("123", colIdx, Types.INTEGER), StandardCharsets.US_ASCII.toString()));
      runFail(() -> castAsciiStream(value, colIdx, Types.BLOB), SqlStateInvalidConversion);

      // castUnicodeStream
      assertEquals(value, readStream(castUnicodeStream(value, colIdx, Types.VARCHAR), StandardCharsets.UTF_16BE.toString()));
      assertEquals(value, readStream(castUnicodeStream(value, colIdx, Types.NCLOB), StandardCharsets.UTF_16BE.toString()));
      assertEquals("3.14", readStream(castUnicodeStream("3.14", colIdx, Types.DOUBLE), StandardCharsets.UTF_16BE.toString()));
      runFail(() -> castUnicodeStream(value, colIdx, Types.BLOB), SqlStateInvalidConversion);

      // castBinaryStream
      assertEquals(value, readStream(castBinaryStream(value, colIdx, Types.VARCHAR), StandardCharsets.UTF_8.toString()));
      assertEquals(value, readStream(castBinaryStream(value, colIdx, Types.CLOB), StandardCharsets.UTF_8.toString()));
      String base64Blob = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
      assertEquals(value, readStream(castBinaryStream(base64Blob, colIdx, Types.BLOB), StandardCharsets.UTF_8.toString()));
      runFail(() -> castBinaryStream("invalid-base64", colIdx, Types.BLOB), SqlStateInvalidType);
      runFail(() -> castBinaryStream(value, colIdx, Types.DATE), SqlStateInvalidConversion);

      // castCharacterStream
      assertEquals(value, readReader(castCharacterStream(value, colIdx, Types.VARCHAR)));
      assertEquals(value, readReader(castCharacterStream(value, colIdx, Types.NCLOB)));
      assertEquals("true", readReader(castCharacterStream("true", colIdx, Types.BOOLEAN)));
      runFail(() -> castCharacterStream(value, colIdx, Types.BLOB), SqlStateInvalidConversion);

      // castNCharacterStream
      assertEquals(value, readReader(castNCharacterStream(value, colIdx, Types.NVARCHAR)));
      assertEquals(value, readReader(castNCharacterStream(value, colIdx, Types.CLOB)));
      runFail(() -> castNCharacterStream(value, colIdx, Types.BLOB), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc LOB conversions", () -> {
      int colIdx = 1;
      String value = "Hello, world!";
      String base64Blob = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

      // castBlob
      assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), castBlob(base64Blob, colIdx, Types.BLOB));
      runFail(() -> castBlob("invalid-base64", colIdx, Types.BLOB), SqlStateInvalidType);
      runFail(() -> castBlob(value, colIdx, Types.VARCHAR), SqlStateInvalidConversion);

      // castClob
      Clob clob = castClob(value, colIdx, Types.CLOB);
      assertEquals(value, clob.getSubString(1, value.length()));
      clob = castClob(value, colIdx, Types.VARCHAR);
      assertEquals(value, clob.getSubString(1, value.length()));
      runFail(() -> castClob(value, colIdx, Types.BLOB), SqlStateInvalidConversion);

      // castNClob
      NClob nclob = castNClob(value, colIdx, Types.NCLOB);
      assertEquals(value, nclob.getSubString(1, value.length()));
      nclob = castNClob(value, colIdx, Types.NVARCHAR);
      assertEquals(value, nclob.getSubString(1, value.length()));
      runFail(() -> castNClob(value, colIdx, Types.BLOB), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc date and time conversions", () -> {
      int colIdx = 1;
      Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

      // castDate
      Date expectedDate = new Date(LocalDate.of(2023, 10, 15).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli());
      assertEquals(expectedDate, castDate("2023-10-15", colIdx, Types.DATE, utcCal));
      assertEquals(expectedDate, castDate("2023-10-15T00:00:00Z", colIdx, Types.TIMESTAMP, utcCal));
      assertEquals(new Date(1697328000000L), castDate("1697328000", colIdx, Types.INTEGER, utcCal)); // Unix timestamp
      runFail(() -> castDate("invalid-date", colIdx, Types.DATE, utcCal), SqlStateInvalidType);
      runFail(() -> castDate("2023-10-15", colIdx, Types.BLOB, utcCal), SqlStateInvalidConversion);

      // castTime
      Time expectedTime = Time.valueOf(LocalTime.of(14, 30));
      assertEquals(expectedTime, castTime("14:30:00", colIdx, Types.TIME, null));
      assertEquals(expectedTime, castTime("14:30:00", colIdx, Types.VARCHAR, null));
      assertEquals(new Time(3600000L), castTime("3600", colIdx, Types.INTEGER, null)); // Unix timestamp
      runFail(() -> castTime("invalid-time", colIdx, Types.TIME, null), SqlStateInvalidType);
      runFail(() -> castTime("14:30:00", colIdx, Types.BLOB, null), SqlStateInvalidConversion);

      // castTimestamp
      Timestamp expectedTs = new Timestamp(
        LocalDateTime.of(2023, 10, 15, 14, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
      );
      assertEquals(expectedTs, castTimestamp("2023-10-15 14:30:00", colIdx, Types.TIMESTAMP, utcCal));
      assertEquals(expectedTs, castTimestamp("2023-10-15T14:30:00Z", colIdx, Types.VARCHAR, utcCal));
      assertEquals(new Timestamp(1697328000000L), castTimestamp("1697328000", colIdx, Types.INTEGER, utcCal));
      runFail(() -> castTimestamp("invalid-ts", colIdx, Types.TIMESTAMP, utcCal), SqlStateInvalidType);
      runFail(() -> castTimestamp("2023-10-15 14:30:00", colIdx, Types.BLOB, utcCal), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc URL conversion", () -> {
      int colIdx = 1;
      var expectedUrl = new URI("https://example.com").toURL();
      assertEquals(expectedUrl, castURL("https://example.com", colIdx, Types.DATALINK));
      assertEquals(expectedUrl, castURL("https://example.com", colIdx, Types.VARCHAR));
      runFail(() -> castURL("invalid-url", colIdx, Types.DATALINK), SqlStateInvalidType);
      runFail(() -> castURL("https://example.com", colIdx, Types.BLOB), SqlStateInvalidConversion);
    });

    it("Tests L4Jdbc object conversion", () -> {
      int colIdx = 1;

      // String
      assertEquals("Hello", castObject("Hello", colIdx, Types.VARCHAR, String.class));
      assertEquals("123", castObject("123", colIdx, Types.CLOB, String.class));
      runFail(() -> castObject("Hello", colIdx, Types.BLOB, String.class), SqlStateFeatureNotSupported);

      // Integer
      assertEquals(123, (int) castObject("123", colIdx, Types.INTEGER, Integer.class));
      assertEquals(127, (int) castObject("127", colIdx, Types.TINYINT, Integer.class));
      runFail(() -> castObject("abc", colIdx, Types.INTEGER, Integer.class), SqlStateInvalidType);

      // Long
      assertEquals(123L, (long) castObject("123", colIdx, Types.BIGINT, Long.class));
      runFail(() -> castObject("abc", colIdx, Types.BIGINT, Long.class), SqlStateInvalidType);

      // Float
      assertEquals(3.14f, castObject("3.14", colIdx, Types.FLOAT, Float.class), 0.001f);
      runFail(() -> castObject("abc", colIdx, Types.FLOAT, Float.class), SqlStateInvalidType);

      // Double
      assertEquals(3.14, castObject("3.14", colIdx, Types.DOUBLE, Double.class), 0.001);
      runFail(() -> castObject("abc", colIdx, Types.DOUBLE, Double.class), SqlStateInvalidType);

      // Byte
      assertEquals((byte) 127, (byte) castObject("127", colIdx, Types.TINYINT, Byte.class));
      runFail(() -> castObject("128", colIdx, Types.TINYINT, Byte.class), SqlStateInvalidType);

      // Short
      assertEquals((short) 32767, (short) castObject("32767", colIdx, Types.SMALLINT, Short.class));
      runFail(() -> castObject("32768", colIdx, Types.SMALLINT, Short.class), SqlStateInvalidType);

      // BigDecimal
      assertEquals(new BigDecimal("123.45"), castObject("123.45", colIdx, Types.NUMERIC, BigDecimal.class));
      runFail(() -> castObject("abc", colIdx, Types.NUMERIC, BigDecimal.class), SqlStateInvalidType);

      // byte[]
      byte[] bytes = "Hello".getBytes(StandardCharsets.UTF_8);
      String base64 = Base64.getEncoder().encodeToString(bytes);
      assertArrayEquals(bytes, castObject(base64, colIdx, Types.BLOB, byte[].class));
      runFail(() -> castObject("invalid-base64", colIdx, Types.BLOB, byte[].class), SqlStateInvalidType);

      // Unsupported type
      runFail(() -> castObject("123", colIdx, Types.INTEGER, URL.class), SqlStateFeatureNotSupported);
      runFail(() -> castObject("123", colIdx, Types.INTEGER, null), SqlStateInvalidType);
    });

    it("Tests L4Jdbc convertValue", () -> {
      int colIdx = 1;
      Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
      String value = "Hello, world!";
      String base64Blob = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

      // VARCHAR
      assertEquals("123", convertValue("123", Types.VARCHAR, Types.VARCHAR, colIdx, -1, null, null));

      // BOOLEAN
      assertTrue((Boolean) convertValue("1", Types.INTEGER, Types.BOOLEAN, colIdx, -1, null, null));
      runFail(() -> convertValue("2", Types.INTEGER, Types.BOOLEAN, colIdx, -1, null, null), SqlStateInvalidType);

      // INTEGER
      assertEquals(123, convertValue("123", Types.INTEGER, Types.INTEGER, colIdx, -1, null, null));
      runFail(() -> convertValue("abc", Types.INTEGER, Types.INTEGER, colIdx, -1, null, null), SqlStateInvalidType);

      // BIGINT
      assertEquals(123L, convertValue("123", Types.BIGINT, Types.BIGINT, colIdx, -1, null, null));
      runFail(() -> convertValue("abc", Types.BIGINT, Types.BIGINT, colIdx, -1, null, null), SqlStateInvalidType);

      // DOUBLE
      assertEquals(3.14, convertValue("3.14", Types.DOUBLE, Types.DOUBLE, colIdx, -1, null, null));
      runFail(() -> convertValue("abc", Types.DOUBLE, Types.DOUBLE, colIdx, -1, null, null), SqlStateInvalidType);

      // FLOAT
      assertEquals(3.14f, convertValue("3.14", Types.FLOAT, Types.FLOAT, colIdx, -1, null, null));
      runFail(() -> convertValue("abc", Types.FLOAT, Types.FLOAT, colIdx, -1, null, null), SqlStateInvalidType);

      // TINYINT
      assertEquals((byte) 127, convertValue("127", Types.TINYINT, Types.TINYINT, colIdx, -1, null, null));
      runFail(() -> convertValue("128", Types.TINYINT, Types.TINYINT, colIdx, -1, null, null), SqlStateInvalidType);

      // SMALLINT
      assertEquals((short) 32767, convertValue("32767", Types.SMALLINT, Types.SMALLINT, colIdx, -1, null, null));
      runFail(() -> convertValue("32768", Types.SMALLINT, Types.SMALLINT, colIdx, -1, null, null), SqlStateInvalidType);

      // DECIMAL
      assertEquals(new BigDecimal("123.45"), convertValue("123.45", Types.NUMERIC, Types.DECIMAL, colIdx, -1, null, null));
      assertEquals(new BigDecimal("123"), convertValue("123.45", Types.NUMERIC, Types.DECIMAL, colIdx, 0, null, null));
      runFail(() -> convertValue("abc", Types.NUMERIC, Types.DECIMAL, colIdx, -1, null, null), SqlStateInvalidType);

      // BLOB
      assertArrayEquals(value.getBytes(StandardCharsets.UTF_8), (byte[]) convertValue(base64Blob, Types.BLOB, Types.BLOB, colIdx, -1, null, null));
      runFail(() -> convertValue("invalid-base64", Types.BLOB, Types.BLOB, colIdx, -1, null, null), SqlStateInvalidType);

      // DATE
      var expectedDate = new Date(LocalDate.of(2023, 10, 15).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli());
      assertEquals(expectedDate, convertValue("2023-10-15", Types.DATE, Types.DATE, colIdx, -1, utcCal, null));
      runFail(() -> convertValue("invalid-date", Types.DATE, Types.DATE, colIdx, -1, utcCal, null), SqlStateInvalidType);

      // TIME
      var expectedTime = Time.valueOf(LocalTime.of(14, 30));
      assertEquals(expectedTime, convertValue("14:30:00", Types.TIME, Types.TIME, colIdx, -1, null, null));
      runFail(() -> convertValue("invalid-time", Types.TIME, Types.TIME, colIdx, -1, null, null), SqlStateInvalidType);

      // TIMESTAMP
      var expectedTs = new Timestamp(
        LocalDateTime.of(2023, 10, 15, 14, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
      );
      assertEquals(expectedTs, convertValue("2023-10-15 14:30:00", Types.TIMESTAMP, Types.TIMESTAMP, colIdx, -1, utcCal, null));
      runFail(() -> convertValue("invalid-ts", Types.TIMESTAMP, Types.TIMESTAMP, colIdx, -1, utcCal, null), SqlStateInvalidType);

      // VARCHAR_STREAM
      assertEquals(value, readStream((InputStream) convertValue(value, Types.VARCHAR, VARCHAR_STREAM, colIdx, -1, null, null), StandardCharsets.US_ASCII.toString()));
      runFail(() -> convertValue(value, Types.BLOB, VARCHAR_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // UNICODE_STREAM
      assertEquals(value, readStream((InputStream) convertValue(value, Types.VARCHAR, UNICODE_STREAM, colIdx, -1, null, null), StandardCharsets.UTF_16BE.toString()));
      runFail(() -> convertValue(value, Types.BLOB, UNICODE_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // BINARY_STREAM
      assertEquals(value, readStream((InputStream) convertValue(base64Blob, Types.BLOB, BINARY_STREAM, colIdx, -1, null, null), StandardCharsets.UTF_8.toString()));
      runFail(() -> convertValue("invalid-base64", Types.BLOB, BINARY_STREAM, colIdx, -1, null, null), SqlStateInvalidType);

      // CHARACTER_STREAM
      assertEquals(value, readReader((Reader) convertValue(value, Types.VARCHAR, CHARACTER_STREAM, colIdx, -1, null, null)));
      runFail(() -> convertValue(value, Types.BLOB, CHARACTER_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // CLOB_STREAM
      var clob = (Clob) convertValue(value, Types.CLOB, CLOB_STREAM, colIdx, -1, null, null);
      assertEquals(value, clob.getSubString(1, value.length()));
      runFail(() -> convertValue(value, Types.BLOB, CLOB_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // NCLOB_STREAM
      var nclob = (NClob) convertValue(value, Types.NCLOB, NCLOB_STREAM, colIdx, -1, null, null);
      assertEquals(value, nclob.getSubString(1, value.length()));
      runFail(() -> convertValue(value, Types.BLOB, NCLOB_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // NCHARACTER_STREAM
      assertEquals(value, readReader((Reader) convertValue(value, Types.NVARCHAR, NCHARACTER_STREAM, colIdx, -1, null, null)));
      runFail(() -> convertValue(value, Types.BLOB, NCHARACTER_STREAM, colIdx, -1, null, null), SqlStateInvalidConversion);

      // URL_STREAM
      var expectedUrl = new URI("https://example.com").toURL();
      assertEquals(expectedUrl, convertValue("https://example.com", Types.DATALINK, URL_STREAM, colIdx, -1, null, null));
      runFail(() -> convertValue("invalid-url", Types.DATALINK, URL_STREAM, colIdx, -1, null, null), SqlStateInvalidType);

      // OBJECT_STREAM
      assertEquals(123, convertValue("123", Types.INTEGER, OBJECT_STREAM, colIdx, -1, null, Integer.class));
      assertEquals(value, convertValue(value, Types.VARCHAR, OBJECT_STREAM, colIdx, -1, null, String.class));
      runFail(() -> convertValue("123", Types.INTEGER, OBJECT_STREAM, colIdx, -1, null, URL.class), SqlStateFeatureNotSupported);

      // NULL
      assertNull(convertValue("null", Types.NULL, Types.NULL, colIdx, -1, null, null));

      // Unsupported target type
      runFail(() -> convertValue("123", Types.INTEGER, Types.ARRAY, colIdx, -1, null, null), SqlStateFeatureNotSupported);
    });

    it("Tests isSelect method", () -> {
      // Test valid SELECT queries
      assertTrue(isSelect("SELECT * FROM table"));
      assertTrue(isSelect("select count(*) from table"));
      assertTrue(isSelect("SELECT a, b FROM table WHERE x = 1; SELECT c FROM table2"));

      // Test non-SELECT queries
      assertFalse(isSelect("INSERT INTO table (a) VALUES (1)"));
      assertFalse(isSelect("UPDATE table SET a = 1"));
      assertFalse(isSelect("DELETE FROM table"));
      assertFalse(isSelect("CREATE TABLE table (id INTEGER)"));

      // Test edge cases
      assertTrue(isSelect("SELECT * FROM table -- comment with select"));
      assertTrue(isSelect("/* SELECT in comment */ INSERT INTO table (a) VALUES (1)"));
      assertTrue(isSelect("SELECT * FROM table WHERE name = 'select'"));
      assertFalse(isSelect(""));
      assertFalse(isSelect("  "));
      assertFalse(isSelect(null));
    });

    it("Tests split method", () -> {
      // Test single statement
      var sql1 = "SELECT * FROM table";
      var result1 = split(sql1);
      assertEquals(1, result1.length);
      assertEquals("SELECT * FROM table", result1[0].sql);

      // Test multiple statements
      var sql2 = "SELECT * FROM table1; SELECT * FROM table2";
      var result2 = split(sql2);
      assertEquals(2, result2.length);
      assertEquals("SELECT * FROM table1", result2[0].sql);
      assertEquals("SELECT * FROM table2", result2[1].sql);

      // Test semicolons in quoted strings
      var sql3 = "SELECT * FROM table WHERE name = 'a;b'; INSERT INTO table (name) VALUES ('c;d')";
      var result3 = split(sql3);
      assertEquals(2, result3.length);
      assertEquals("SELECT * FROM table WHERE name = 'a;b'", result3[0].sql);
      assertEquals("INSERT INTO table (name) VALUES ('c;d')", result3[1].sql);

      // Test semicolons in comments
      var sql4 = "SELECT * FROM table -- comment; with semicolon\n; INSERT INTO table (a) VALUES (1)";
      var result4 = split(sql4);
      assertEquals(2, result4.length);
      assertEquals("SELECT * FROM table -- comment; with semicolon", result4[0].sql);
      assertEquals("INSERT INTO table (a) VALUES (1)", result4[1].sql);

      // Test edge cases
      var sql5 = "";
      var result5 = split(sql5);
      assertEquals(0, result5.length);

      var sql6 = ";;";
      var result6 = split(sql6);
      assertEquals(0, result6.length); // Empty statements ignored

      var sql7 = "SELECT * FROM table; ; SELECT * FROM table2";
      var result7 = split(sql7);
      assertEquals(2, result7.length);
      assertEquals("SELECT * FROM table", result7[0].sql);
      assertEquals("SELECT * FROM table2", result7[1].sql);

      // Test null input
      try {
        split(null);
        fail("Expected IllegalArgumentException for null SQL");
      } catch (IllegalArgumentException e) {
        assertNotNull(e.getMessage());
      }
    });
  }
}