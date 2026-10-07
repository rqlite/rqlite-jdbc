package io.rqlite.jdbc;

import io.rqlite.client.L4Statement;
import javax.sql.rowset.serial.SerialClob;
import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;

import static io.rqlite.jdbc.L4Err.*;
import static java.sql.Types.*;
import static java.lang.String.format;

public class L4Jdbc {

  public static final int VARCHAR_STREAM    = Types.VARCHAR   + 1000; // Custom type to distinguish stream
  public static final int UNICODE_STREAM    = Types.VARCHAR   + 1001; // Custom type for deprecated Unicode stream
  public static final int BINARY_STREAM     = Types.BLOB      + 1000; // Custom type for binary stream
  public static final int CHARACTER_STREAM  = Types.VARCHAR   + 1002;
  public static final int CLOB_STREAM       = Types.VARCHAR   + 1003;
  public static final int OBJECT_STREAM     = Types.OTHER     + 1000;
  public static final int URL_STREAM        = Types.DATALINK  + 1000;
  public static final int NCLOB_STREAM      = Types.NCLOB     + 1000;
  public static final int NCHARACTER_STREAM = Types.NVARCHAR  + 1000;

  // constants for rqlite types
  public static final String RQ_INT       = "INT";
  public static final String RQ_INTEGER   = "INTEGER";
  public static final String RQ_NUMERIC   = "NUMERIC";
  public static final String RQ_BOOLEAN   = "BOOLEAN";
  public static final String RQ_TINYINT   = "TINYINT";
  public static final String RQ_SMALLINT  = "SMALLINT";
  public static final String RQ_BIGINT    = "BIGINT";
  public static final String RQ_FLOAT     = "FLOAT";
  public static final String RQ_DOUBLE    = "DOUBLE";
  public static final String RQ_TEXT      = "TEXT";
  public static final String RQ_VARCHAR   = "VARCHAR";
  public static final String RQ_DATE      = "DATE";
  public static final String RQ_TIME      = "TIME";
  public static final String RQ_TIMESTAMP = "TIMESTAMP";
  public static final String RQ_DATALINK  = "DATALINK";
  public static final String RQ_CLOB      = "CLOB";
  public static final String RQ_NCLOB     = "NCLOB";
  public static final String RQ_NVARCHAR  = "NVARCHAR";
  public static final String RQ_BLOB      = "BLOB";
  public static final String RQ_NULL      = "NULL";
  public static final String RQ_REAL      = "REAL"; // Alias for FLOAT in RQLite

  public static final String[] RQ_TYPES = new String[] {
    RQ_INTEGER, RQ_NUMERIC, RQ_BOOLEAN, RQ_TINYINT,
    RQ_SMALLINT, RQ_BIGINT, RQ_FLOAT, RQ_DOUBLE,
    RQ_VARCHAR, RQ_DATE, RQ_TIME, RQ_TIMESTAMP,
    RQ_DATALINK, RQ_CLOB, RQ_NCLOB, RQ_NVARCHAR,
    RQ_BLOB, RQ_NULL, RQ_REAL
  };

  public static String loadResourceAsString(String resourcePath) {
    try (var is = L4Jdbc.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static String driverVersion() {
    return loadResourceAsString("/io/rqlite/jdbc/version");
  }

  public static int driverVersionMajor() {
    var ver = driverVersion();
    return Integer.parseInt(ver.split("\\.")[0]);
  }

  public static int driverVersionMinor() {
    var ver = driverVersion();
    return Integer.parseInt(ver.split("\\.")[1]);
  }

  public static boolean anyOf(int sourceType, int ... types) {
    for (var t : types) {
      if (sourceType == t) {
        return true;
      }
    }
    return false;
  }

  public static boolean castBoolean(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, INTEGER, NUMERIC)) {
      try {
        var longVal = Long.parseLong(value);
        if (longVal == 0 || longVal == 1) {
          return longVal == 1;
        }
        throw rangeError(value, columnIndex, BOOLEAN);
      } catch (NumberFormatException e) {
        throw badBoolean(columnIndex, value, e);
      }
    } else if (anyOf(sourceJdbcType, VARCHAR, BOOLEAN)) {
      return Boolean.parseBoolean(value);
    }
    throw castError(value, columnIndex, sourceJdbcType, BOOLEAN);
  }

  public static int castInteger(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, INTEGER, TINYINT, SMALLINT, BOOLEAN, NUMERIC)) {
      try {
        var longVal = Long.parseLong(value);
        if (longVal >= Integer.MIN_VALUE && longVal <= Integer.MAX_VALUE) {
          return (int) longVal;
        }
        throw rangeError(value, columnIndex, INTEGER);
      } catch (NumberFormatException e) {
        throw badInteger(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, INTEGER);
  }

  public static long castLong(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, INTEGER, BIGINT, TINYINT, SMALLINT, BOOLEAN, NUMERIC)) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        throw badLong(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, BIGINT);
  }

  public static float castFloat(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, FLOAT, DOUBLE, NUMERIC)) {
      try {
        return Float.parseFloat(value);
      } catch (NumberFormatException e) {
        throw badFloat(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, FLOAT);
  }

  public static double castDouble(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, FLOAT, DOUBLE, NUMERIC)) {
      try {
        return Double.parseDouble(value);
      } catch (NumberFormatException e) {
        throw badDouble(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, DOUBLE);
  }

  public static byte castByte(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType,  INTEGER, TINYINT, BOOLEAN, NUMERIC)) {
      try {
        var longVal = Long.parseLong(value);
        if (longVal >= Byte.MIN_VALUE && longVal <= Byte.MAX_VALUE) {
          return (byte) longVal;
        }
        throw rangeError(value, columnIndex, TINYINT);
      } catch (NumberFormatException e) {
        throw badByte(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, TINYINT);
  }

  public static short castShort(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, INTEGER, TINYINT, SMALLINT, BOOLEAN, NUMERIC)) {
      try {
        var longVal = Long.parseLong(value);
        if (longVal >= Short.MIN_VALUE && longVal <= Short.MAX_VALUE) {
          return (short) longVal;
        }
        throw rangeError(value, columnIndex, SMALLINT);
      } catch (NumberFormatException e) {
        throw badShort(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, SMALLINT);
  }

  public static BigDecimal castBigDecimal(String value, int columnIndex, int sourceJdbcType, int scale) throws SQLException {
    if (anyOf(sourceJdbcType, INTEGER, FLOAT, DOUBLE, VARCHAR, NUMERIC, BOOLEAN, TINYINT, SMALLINT, BIGINT)) {
      try {
        var bd = new BigDecimal(value);
        if (scale != -1) {
          bd =  bd.setScale(scale, RoundingMode.HALF_UP);
        }
        return bd;
      } catch (NumberFormatException e) {
        throw badBigDecimal(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, NUMERIC);
  }

  /** Source JDBC types whose values can be rendered as text/streams. */
  private static final int[] TEXT_TYPES = {
    VARCHAR, CLOB, NCLOB, NVARCHAR, INTEGER, DOUBLE, NUMERIC, BOOLEAN
  };

  private static byte[] decodeBase64(String value, int columnIndex) throws SQLException {
    try {
      return Base64.getDecoder().decode(value);
    } catch (IllegalArgumentException e) {
      throw badB64(columnIndex, value, e);
    }
  }

  public static InputStream castAsciiStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, TEXT_TYPES)) {
      var asciiBytes = value.getBytes(StandardCharsets.US_ASCII); // Convert non-ASCII to '?'
      return new ByteArrayInputStream(asciiBytes);
    }
    throw castError(value, columnIndex, sourceJdbcType, VARCHAR_STREAM);
  }

  public static InputStream castUnicodeStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, TEXT_TYPES)) {
      var unicodeBytes = value.getBytes(StandardCharsets.UTF_16BE); // Encode as UTF-16BE
      return new ByteArrayInputStream(unicodeBytes);
    }
    throw castError(value, columnIndex, sourceJdbcType, UNICODE_STREAM);
  }

  public static InputStream castBinaryStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (sourceJdbcType == BLOB) {
      return new ByteArrayInputStream(decodeBase64(value, columnIndex));
    } else if (anyOf(sourceJdbcType, TEXT_TYPES)) {
      return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); // Encode as UTF-8
    }
    throw castError(value, columnIndex, sourceJdbcType, BINARY_STREAM);
  }

  public static Reader castCharacterStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, TEXT_TYPES)) {
      return new StringReader(value);
    }
    throw castError(value, columnIndex, sourceJdbcType, CHARACTER_STREAM);
  }

  public static byte[] castBlob(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (sourceJdbcType == BLOB) {
      return decodeBase64(value, columnIndex);
    }
    throw castError(value, columnIndex, sourceJdbcType, BLOB);
  }

  public static Clob castClob(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, CLOB, NCLOB, NVARCHAR)) {
      return new SerialClob(value.toCharArray());
    }
    throw castError(value, columnIndex, sourceJdbcType, CLOB);
  }

  public static Date castDate(String value, int columnIndex, int sourceJdbcType, Calendar cal) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, DATE, TIMESTAMP)) {
      try {
        // Try parsing as ISO timestamp (e.g., "2023-10-15T00:00:00Z")
        try {
          var instant = Instant.parse(value); // Handles ISO 8601 with UTC (Z) - absolute, no TZ needed
          return new Date(instant.toEpochMilli());
        } catch (DateTimeParseException e) {
          // Fallback to ISO local date (e.g., "2023-10-15")
          var localDate = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
          var calendar = cal != null ? cal : Calendar.getInstance();
          var zdt = localDate.atStartOfDay(calendar.getTimeZone().toZoneId());
          return new Date(zdt.toInstant().toEpochMilli());
        }
      } catch (DateTimeParseException e) {
        throw badDate(columnIndex, value, e);
      }
    } else if (sourceJdbcType == INTEGER) {
      try {
        var seconds = Long.parseLong(value);
        return new Date(seconds * 1000L); // Unix timestamp
      } catch (NumberFormatException e) {
        throw badTimestamp(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, DATE);
  }

  public static Time castTime(String value, int columnIndex, int sourceJdbcType, Calendar cal) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, TIME, TIMESTAMP)) {
      try {
        var localTime = LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        var calendar = cal != null ? cal : Calendar.getInstance();
        var ldt = localTime.atDate(LocalDate.ofEpochDay(0)); // Epoch day for Time
        var zdt = ldt.atZone(calendar.getTimeZone().toZoneId());
        return new Time(zdt.toInstant().toEpochMilli());
      } catch (DateTimeParseException e) {
        throw badTime(columnIndex, value, e);
      }
    } else if (sourceJdbcType == INTEGER) {
      try {
        var seconds = Long.parseLong(value);
        return new Time(seconds * 1000L); // Unix timestamp
      } catch (NumberFormatException e) {
        throw badTimestamp(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, TIME);
  }

  public static Timestamp castTimestamp(Object raw, int columnIndex, int sourceJdbcType, Calendar cal) throws SQLException {
    if (raw instanceof Timestamp) {
      var utcTs = L4Utc.utcDateTimeOf((Timestamp) raw);
      var ms = utcTs.toInstant(ZoneOffset.UTC).toEpochMilli();
      return new Timestamp(ms);
    }
    var value = raw.toString();
    if (anyOf(sourceJdbcType, VARCHAR, TIMESTAMP, DATE)) {
      try {
        // Try parsing as ISO timestamp (e.g., "2023-10-15T14:30:00Z")
        try {
          var instant = Instant.parse(value); // Handles ISO 8601 with UTC (Z) - absolute, no TZ needed
          return new Timestamp(instant.toEpochMilli());
        } catch (DateTimeParseException e) {
          // Fallback to ISO local date-time (e.g., "2023-10-15 14:30:00")
          DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                  .appendPattern("yyyy-MM-dd HH:mm:ss")
                  .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true) // optional .SSS... up to nanoseconds
                  .toFormatter();
          var localDateTime = LocalDateTime.parse(value, formatter);
          var calendar = cal != null ? cal : Calendar.getInstance();
          var zdt = localDateTime.atZone(calendar.getTimeZone().toZoneId());
          return new Timestamp(zdt.toInstant().toEpochMilli());
        }
      } catch (DateTimeParseException e) {
        throw badTimestamp(columnIndex, value, e);
      }
    } else if (sourceJdbcType == INTEGER) {
      try {
        var seconds = Long.parseLong(value);
        return new Timestamp(seconds * 1000L); // Unix timestamp
      } catch (NumberFormatException e) {
        throw badTimestamp(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, Types.TIMESTAMP);
  }

  public static URL castURL(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, DATALINK)) {
      try {
        return new URI(value).toURL();
      } catch (Exception e) {
        throw badUrl(columnIndex, value, e);
      }
    }
    throw castError(value, columnIndex, sourceJdbcType, DATALINK);
  }

  public static NClob castNClob(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, NCLOB, NVARCHAR, CLOB)) {
      var clob = new L4NClob();
      clob.setString(1, value);
      return clob;
    }
    throw castError(value, columnIndex, sourceJdbcType, NCLOB);
  }

  public static Reader castNCharacterStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, NVARCHAR, CLOB, NCLOB)) {
      return new StringReader(value);
    }
    throw castError(value, columnIndex, sourceJdbcType, NCHARACTER_STREAM);
  }

  public static <T> T castObject(String value, int columnIndex, int sourceJdbcType, Class<T> type) throws SQLException {
    if (type == null) {
      throw badType(columnIndex, value);
    }
    Object result;
    if (type == String.class) {
      result = value;
    } else if (type == Integer.class) {
      result = castInteger(value, columnIndex, sourceJdbcType);
    } else if (type == Long.class) {
      result = castLong(value, columnIndex, sourceJdbcType);
    } else if (type == Float.class) {
      result = castFloat(value, columnIndex, sourceJdbcType);
    } else if (type == Double.class) {
      result = castDouble(value, columnIndex, sourceJdbcType);
    } else if (type == Byte.class) {
      result = castByte(value, columnIndex, sourceJdbcType);
    } else if (type == Short.class) {
      result = castShort(value, columnIndex, sourceJdbcType);
    } else if (type == BigDecimal.class) {
      result = castBigDecimal(value, columnIndex, sourceJdbcType, -1);
    } else if (type == Boolean.class) {
      result = castBoolean(value, columnIndex, sourceJdbcType);
    } else if (type == byte[].class) {
      result = castBlob(value, columnIndex, sourceJdbcType);
    } else {
      throw badConversion(columnIndex, sourceJdbcType, type);
    }
    return type.cast(result);
  }

  /**
   * Used for resultset getXXX methods.
   */
  public static Object convertValue(String value, int sourceJdbcType, int targetJdbcType,
                                    int columnIndex, int scale, Calendar cal, Class<?> type) throws SQLException {
    try {
      switch (targetJdbcType) {
        case CHAR:
        case CLOB:
        case DATALINK:
        case VARCHAR:
        case NCLOB:
        case NVARCHAR:          return value;
        case BOOLEAN:           return castBoolean(value, columnIndex, sourceJdbcType);
        case INTEGER:           return castInteger(value, columnIndex, sourceJdbcType);
        case BIGINT:            return castLong(value, columnIndex, sourceJdbcType);
        case DOUBLE:            return castDouble(value, columnIndex, sourceJdbcType);
        case FLOAT:             return castFloat(value, columnIndex, sourceJdbcType);
        case BLOB:              return castBlob(value, columnIndex, sourceJdbcType);
        case TINYINT:           return castByte(value, columnIndex, sourceJdbcType);
        case SMALLINT:          return castShort(value, columnIndex, sourceJdbcType);
        case NUMERIC:
        case DECIMAL:           return castBigDecimal(value, columnIndex, sourceJdbcType, scale);
        case DATE:              return castDate(value, columnIndex, sourceJdbcType, cal);
        case TIME:              return castTime(value, columnIndex, sourceJdbcType, cal);
        case TIMESTAMP:         return castTimestamp(value, columnIndex, sourceJdbcType, cal);
        case VARCHAR_STREAM:    return castAsciiStream(value, columnIndex, sourceJdbcType);
        case UNICODE_STREAM:    return castUnicodeStream(value, columnIndex, sourceJdbcType);
        case BINARY_STREAM:     return castBinaryStream(value, columnIndex, sourceJdbcType);
        case CHARACTER_STREAM:  return castCharacterStream(value, columnIndex, sourceJdbcType);
        case CLOB_STREAM:       return castClob(value, columnIndex, sourceJdbcType);
        case OBJECT_STREAM:     return castObject(value, columnIndex, sourceJdbcType, type);
        case URL_STREAM:        return castURL(value, columnIndex, sourceJdbcType);
        case NCLOB_STREAM:      return castNClob(value, columnIndex, sourceJdbcType);
        case NCHARACTER_STREAM: return castNCharacterStream(value, columnIndex, sourceJdbcType);
        case NULL:              return null;
        default:
          throw notSupported(format("JDBC type %d for column %d", targetJdbcType, columnIndex));
      }
    } catch (Exception e) {
      throw badConversion(columnIndex, value, e);
    }
  }

  /**
   * Used for resultset setXXX methods.
   */
  public static Object convertParameter(Object x, int targetSqlType) throws SQLException {
    if (x == null) {
      return null;
    }
    try {
      switch (targetSqlType) {
        case BOOLEAN:   return castBoolean(x.toString(), 1, VARCHAR) ? 1 : 0;
        case TINYINT:   return castByte(x.toString(), 1, TINYINT);
        case SMALLINT:  return castShort(x.toString(), 1, SMALLINT);
        case INTEGER:   return castInteger(x.toString(), 1, INTEGER);
        case BIGINT:    return castLong(x.toString(), 1, BIGINT);
        case FLOAT:     return castFloat(x.toString(), 1, FLOAT);
        case DOUBLE:    return castDouble(x.toString(), 1, DOUBLE);
        case NUMERIC:
        case DECIMAL:   return castBigDecimal(x.toString(), 1, NUMERIC, -1);
        case VARCHAR:
        case NVARCHAR:
        case CLOB:
        case NCLOB:     return x.toString();
        case DATE:      return L4Utc.utcOf(castDate(x.toString(), 1, DATE, null)).toString();
        case TIME:      return L4Utc.utcOf(castTime(x.toString(), 1, TIME, null)).toString();
        case TIMESTAMP: return L4Utc.utcFmtOf(castTimestamp(x, 1, TIMESTAMP, null));
        case DATALINK:  return castURL(x.toString(), 1, DATALINK).toString();
        case BLOB:
          if (x instanceof byte[]) {
            return x;
          }
          throw badParam("Invalid BLOB data");
        default:
          throw notSupported(format("Unsupported SQL type: [%s]", targetSqlType));
      }
    } catch (Exception e) {
      throw badParam(e);
    }
  }

  public static int getJdbcType(String rqliteType) {
    if (rqliteType == null) {
      throw new IllegalArgumentException("type cannot be null");
    }
    if (rqliteType.isEmpty()) {
      return NULL; // SELECT NULL AS TABLE_CAT, etc...
    }
    var t = RqType.fromBase(rqBaseType(rqliteType));
    return t == null ? -1 : t.jdbcType;
  }

  /**
   * Normalizes a declared rqlite/SQLite type by stripping any size/precision suffix
   * (e.g. {@code VARCHAR(255)} or {@code DECIMAL(10,2)}) and upper-casing it.
   */
  public static String rqBaseType(String rqliteType) {
    if (rqliteType == null) {
      return RQ_NULL;
    }
    var t = rqliteType.trim().toUpperCase();
    if (t.isEmpty()) {
      return RQ_NULL;
    }
    return t.split("[(),]")[0].trim();
  }

  public static boolean getJdbcTypeSigned(String rqType) {
    var t = RqType.fromBase(rqBaseType(rqType));
    return t != null && t.signed;
  }

  public static int getJdbcTypePrecision(String rqliteType) {
    var t = RqType.fromBase(rqBaseType(rqliteType));
    return t == null ? 0 : t.precision;
  }

  public static Class<?> getJdbcTypeClass(String type) {
    var t = RqType.fromBase(rqBaseType(type));
    return t == null ? Object.class : t.javaClass;
  }

  public static String rqTypeOf(Class<?> clazz) {
    var t = RqType.fromClass(clazz);
    return t == null ? RQ_NULL : t.name();
  }

  public static String rqTypeOf(Object o) {
    if (o == null) {
      return RQ_NULL;
    }
    return rqTypeOf(o.getClass());
  }

  public static String getJdbcTypeClassName(String type) {
    return getJdbcTypeClass(type).getCanonicalName();
  }

  public static int getJdbcTypeColumnDisplaySize(String type) {
    var t = RqType.fromBase(rqBaseType(type));
    return t == null ? 4 : t.displaySize;
  }

  /**
   * If the character at {@code i} begins a quoted region or comment, returns the index just
   * past that region; otherwise returns {@code -1}. Handles {@code '...'} (with {@code ''}
   * escape), {@code "..."} (with {@code ""} escape), {@code [...]} and backtick identifiers,
   * {@code --} line comments and block comments.
   */
  private static int skipQuotedOrComment(String s, int i) {
    int n = s.length();
    char c = s.charAt(i);
    if (c == '\'' || c == '"') {
      int j = i + 1;
      while (j < n) {
        if (s.charAt(j) == c) {
          if (j + 1 < n && s.charAt(j + 1) == c) {
            j += 2;
            continue;
          }
          return j + 1;
        }
        j++;
      }
      return n;
    }
    if (c == '[') {
      int j = s.indexOf(']', i + 1);
      return j < 0 ? n : j + 1;
    }
    if (c == '`') {
      int j = s.indexOf('`', i + 1);
      return j < 0 ? n : j + 1;
    }
    if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
      int j = s.indexOf('\n', i + 2);
      return j < 0 ? n : j + 1;
    }
    if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
      int j = s.indexOf("*/", i + 2);
      return j < 0 ? n : j + 2;
    }
    return -1;
  }

  /** Skips leading whitespace and comments, returning the index of the first meaningful char. */
  private static int skipLeadingTrivia(String s) {
    int i = 0;
    int n = s.length();
    while (i < n) {
      char c = s.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
        i = skipQuotedOrComment(s, i);
        continue;
      }
      if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
        i = skipQuotedOrComment(s, i);
        continue;
      }
      break;
    }
    return i;
  }

  public static boolean isSelect(String rawSql) {
    if (rawSql == null) {
      return false;
    }
    int start = skipLeadingTrivia(rawSql);
    if (start >= rawSql.length()) {
      return false;
    }
    var m = java.util.regex.Pattern.compile("^([A-Za-z_]+)").matcher(rawSql.substring(start));
    if (!m.find()) {
      return false;
    }
    var keyword = m.group(1).toUpperCase();
    return keyword.equals("SELECT")
      || keyword.equals("VALUES")
      || keyword.equals("PRAGMA")
      || keyword.equals("EXPLAIN");
  }

  public static String quote(String val) {
    return val.replace("'", "''");
  }

  /**
   * A prepared-statement placeholder discovered in SQL text.
   */
  public static final class Placeholder {
    public final int index;   // 1-based positional index, or -1 for a named placeholder
    public final String name; // named placeholder (without sigil), or null
    public final int offset;  // character offset in the SQL text

    Placeholder(int index, String name, int offset) {
      this.index = index;
      this.name = name;
      this.offset = offset;
    }

    public boolean isNamed() {
      return name != null;
    }

    @Override public String toString() {
      return isNamed() ? ":" + name : "?" + index;
    }
  }

  private static boolean isIdentChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  /**
   * Scans SQL for SQLite parameter placeholders ({@code ?}, {@code ?NNN}, {@code :name},
   * {@code @name}, {@code $name}), ignoring placeholders inside strings, comments and
   * quoted identifiers.
   */
  public static List<Placeholder> scanPlaceholders(String rawSql) {
    var out = new ArrayList<Placeholder>();
    if (rawSql == null || rawSql.isEmpty()) {
      return out;
    }
    int i = 0;
    int n = rawSql.length();
    int next = 1;
    while (i < n) {
      int skip = skipQuotedOrComment(rawSql, i);
      if (skip > i) {
        i = skip;
        continue;
      }
      char c = rawSql.charAt(i);
      if (c == '?') {
        int start = i;
        int j = i + 1;
        int idx;
        if (j < n && Character.isDigit(rawSql.charAt(j))) {
          int k = j;
          while (k < n && Character.isDigit(rawSql.charAt(k))) {
            k++;
          }
          idx = Integer.parseInt(rawSql.substring(j, k));
          next = idx + 1;
          i = k;
        } else {
          idx = next++;
          i++;
        }
        out.add(new Placeholder(idx, null, start));
        continue;
      }
      if (c == ':' || c == '@' || c == '$') {
        var prevIdent = i > 0 && isIdentChar(rawSql.charAt(i - 1));
        int j = i + 1;
        if (!prevIdent && j < n && isIdentChar(rawSql.charAt(j))) {
          int k = j;
          while (k < n && isIdentChar(rawSql.charAt(k))) {
            k++;
          }
          out.add(new Placeholder(-1, rawSql.substring(j, k), i));
          i = k;
          continue;
        }
      }
      i++;
    }
    return out;
  }

  /** Returns the highest positional placeholder index (0 if none). */
  public static int positionalParameterCount(List<Placeholder> placeholders) {
    int max = 0;
    for (var p : placeholders) {
      if (!p.isNamed() && p.index > max) {
        max = p.index;
      }
    }
    return max;
  }

  /** Returns the distinct named placeholders, in order of appearance. */
  public static List<String> namedParameterNames(List<Placeholder> placeholders) {
    var names = new ArrayList<String>();
    for (var p : placeholders) {
      if (p.isNamed() && !names.contains(p.name)) {
        names.add(p.name);
      }
    }
    return names;
  }

  public static L4Statement[] split(String rawSql) {
    if (rawSql == null) {
      throw new IllegalArgumentException("SQL string cannot be null");
    }
    var sql = rawSql.trim();
    if (sql.isEmpty()) {
      return new L4Statement[0];
    }

    var statements = new ArrayList<String>();
    var currentStatement = new StringBuilder();
    int i = 0;
    int n = sql.length();
    while (i < n) {
      int skip = skipQuotedOrComment(sql, i);
      if (skip > i) {
        currentStatement.append(sql, i, skip);
        i = skip;
        continue;
      }
      char c = sql.charAt(i);
      if (c == ';') {
        var stmt = currentStatement.toString().trim();
        if (!stmt.isEmpty()) {
          statements.add(stmt);
        }
        currentStatement.setLength(0);
        i++;
        continue;
      }
      currentStatement.append(c);
      i++;
    }

    // Add the last statement if non-empty
    var lastStmt = currentStatement.toString().trim();
    if (!lastStmt.isEmpty()) {
      statements.add(lastStmt);
    }

    return statements.stream()
      .map(raw -> new L4Statement().sql(raw))
      .toArray(L4Statement[]::new);
  }

}
