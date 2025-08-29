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
import java.time.format.DateTimeParseException;
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

  public static InputStream castAsciiStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, CLOB, NCLOB, NVARCHAR, INTEGER, DOUBLE, NUMERIC, BOOLEAN)) {
      var asciiBytes = value.getBytes(StandardCharsets.US_ASCII); // Convert non-ASCII to '?'
      return new ByteArrayInputStream(asciiBytes);
    }
    throw castError(value, columnIndex, sourceJdbcType, VARCHAR_STREAM);
  }

  public static InputStream castUnicodeStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, CLOB, NCLOB, NVARCHAR, INTEGER, DOUBLE, NUMERIC, BOOLEAN)) {
      var unicodeBytes = value.getBytes(StandardCharsets.UTF_16BE); // Encode as UTF-16BE
      return new ByteArrayInputStream(unicodeBytes);
    }
    throw castError(value, columnIndex, sourceJdbcType, UNICODE_STREAM);
  }

  public static InputStream castBinaryStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (sourceJdbcType == BLOB) {
      try {
        var bytes = Base64.getDecoder().decode(value);
        return new ByteArrayInputStream(bytes);
      } catch (IllegalArgumentException e) {
        throw badB64(columnIndex, value, e);
      }
    } else if (anyOf(sourceJdbcType, VARCHAR, CLOB, NCLOB, NVARCHAR, INTEGER, DOUBLE, NUMERIC, BOOLEAN)) {
      return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)); // Encode as UTF-8
    }
    throw castError(value, columnIndex, sourceJdbcType, BINARY_STREAM);
  }

  public static Reader castCharacterStream(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (anyOf(sourceJdbcType, VARCHAR, CLOB, NCLOB, NVARCHAR, INTEGER, DOUBLE, NUMERIC, BOOLEAN)) {
      return new StringReader(value);
    }
    throw castError(value, columnIndex, sourceJdbcType, CHARACTER_STREAM);
  }

  public static byte[] castBlob(String value, int columnIndex, int sourceJdbcType) throws SQLException {
    if (sourceJdbcType == BLOB) {
      try {
        return Base64.getDecoder().decode(value);
      } catch (IllegalArgumentException e) {
        throw badB64(columnIndex, value, e);
      }
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
          var localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
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
        case DECIMAL:   return castBigDecimal(x.toString(), 1, NUMERIC, -1).toString();
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
            return Base64.getEncoder().encodeToString((byte[]) x);
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
    var parts = rqliteType.trim().toUpperCase().split("[(),]");
    var rqType = parts[0];
    switch (rqType) {
      case RQ_INTEGER:    return INTEGER;
      case RQ_NUMERIC:    return NUMERIC;
      case RQ_BOOLEAN:    return BOOLEAN;
      case RQ_TINYINT:    return TINYINT;
      case RQ_SMALLINT:   return SMALLINT;
      case RQ_BIGINT:     return BIGINT;
      case RQ_FLOAT:      return FLOAT;
      case RQ_DOUBLE:     return DOUBLE;
      case RQ_TEXT:
      case RQ_VARCHAR:    return VARCHAR;
      case RQ_DATE:       return DATE;
      case RQ_TIME:       return TIME;
      case RQ_TIMESTAMP:  return TIMESTAMP;
      case RQ_DATALINK:   return DATALINK;
      case RQ_CLOB:       return CLOB;
      case RQ_NCLOB:      return NCLOB;
      case RQ_NVARCHAR:   return NVARCHAR;
      case RQ_BLOB:       return BLOB;
      case RQ_NULL:       return NULL;
      case RQ_REAL:       return FLOAT; // RQLite uses REAL as an alias for FLOAT
      default: return -1;
    }
  }

  public static boolean getJdbcTypeSigned(String rqType) {
    if (rqType == null || RQ_NULL.equalsIgnoreCase(rqType)) {
      return false; // NULL or unknown
    }
    var typeUpper = rqType.toUpperCase();
    return typeUpper.equals(RQ_INTEGER)
      || typeUpper.equals(RQ_NUMERIC)
      || typeUpper.equals(RQ_TINYINT)
      || typeUpper.equals(RQ_SMALLINT)
      || typeUpper.equals(RQ_BIGINT)
      || typeUpper.equals(RQ_FLOAT)
      || typeUpper.equals(RQ_DOUBLE);
  }

  public static int getJdbcTypePrecision(String rqliteType) {
    if (rqliteType == null || RQ_NULL.equalsIgnoreCase(rqliteType)) {
      return 0; // NULL or unknown
    }
    var typeUpper = rqliteType.toUpperCase();
    switch (typeUpper) {
      case RQ_INTEGER:    return 10;     // 32-bit integer (approx 10 digits)
      case RQ_NUMERIC:    return 38;     // Arbitrary precision, conservative estimate
      case RQ_BOOLEAN:    return 1;      // 0 or 1
      case RQ_TINYINT:    return 3;      // 3 digits (-128 to 127)
      case RQ_SMALLINT:   return 5;      // 5 digits (-32768 to 32767)
      case RQ_BIGINT:     return 19;     // 64-bit integer (approx 19 digits)
      case RQ_FLOAT:      return 7;      // Single-precision (approx 7 digits)
      case RQ_DOUBLE:     return 15;     // Double-precision (approx 15 digits)
      case RQ_REAL:       return 7;      // Alias for FLOAT, single-precision
      case RQ_VARCHAR:    return 255;    // Arbitrary, conservative default
      case RQ_DATE:       return 10;     // "YYYY-MM-DD"
      case RQ_TIME:       return 8;      // "HH:MM:SS"
      case RQ_TIMESTAMP:  return 19;     // "YYYY-MM-DD HH:MM:SS"
      case RQ_DATALINK:   return 255;    // URL, conservative default
      case RQ_CLOB:       return 65535;  // Large text
      case RQ_NCLOB:      return 65535;  // Large national text
      case RQ_NVARCHAR:   return 255;    // National text, conservative default
      case RQ_BLOB:       return 65535;  // Binary data, conservative default
      default:            return 0;      // Fallback for unknown types
    }
  }

  public static Class<?> getJdbcTypeClass(String type) {
    var typeUpper = type.toUpperCase();
    switch (typeUpper) {
      case RQ_INTEGER:   return Integer.class;
      case RQ_NUMERIC:   return java.math.BigDecimal.class;
      case RQ_BOOLEAN:   return Boolean.class;
      case RQ_TINYINT:   return Byte.class;
      case RQ_SMALLINT:  return Short.class;
      case RQ_BIGINT:    return Long.class;
      case RQ_FLOAT:
      case RQ_REAL:
        return Float.class;
      case RQ_DOUBLE:    return Double.class;
      case RQ_VARCHAR:
      case RQ_NVARCHAR:  return String.class;
      case RQ_DATE:      return java.sql.Date.class;
      case RQ_TIME:      return java.sql.Time.class;
      case RQ_TIMESTAMP: return java.sql.Timestamp.class;
      case RQ_DATALINK:  return java.net.URL.class;
      case RQ_CLOB:      return java.sql.Clob.class;
      case RQ_NCLOB:     return java.sql.NClob.class;
      case RQ_BLOB:      return byte[].class;
      default:           return Object.class;
    }
  }

  public static String rqTypeOf(Class<?> clazz) {
    if (clazz == null) {
      return RQ_NULL;
    }
    if (clazz == Integer.class) {
      return RQ_INTEGER;
    } else if (clazz == java.math.BigDecimal.class) {
      return RQ_NUMERIC;
    } else if (clazz == Boolean.class) {
      return RQ_BOOLEAN;
    } else if (clazz == Byte.class) {
      return RQ_TINYINT;
    } else if (clazz == Short.class) {
      return RQ_SMALLINT;
    } else if (clazz == Long.class) {
      return RQ_BIGINT;
    } else if (clazz == Float.class) {
      return RQ_FLOAT;
    } else if (clazz == Double.class) {
      return RQ_DOUBLE;
    } else if (clazz == String.class) {
      return RQ_VARCHAR; // Prefer VARCHAR over NVARCHAR for String
    } else if (clazz == java.sql.Date.class) {
      return RQ_DATE;
    } else if (clazz == java.sql.Time.class) {
      return RQ_TIME;
    } else if (clazz == java.sql.Timestamp.class) {
      return RQ_TIMESTAMP;
    } else if (clazz == java.net.URL.class) {
      return RQ_DATALINK;
    } else if (clazz == java.sql.Clob.class) {
      return RQ_CLOB;
    } else if (clazz == java.sql.NClob.class) {
      return RQ_NCLOB;
    } else if (clazz == byte[].class) {
      return RQ_BLOB;
    }
    return RQ_NULL;
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
    var typeUpper = type.toUpperCase();
    switch (typeUpper) {
      case RQ_INTEGER:    return 11;   // -2147483648 to 2147483647
      case RQ_NUMERIC:    return 38;   // Arbitrary precision, conservative estimate
      case RQ_BOOLEAN:    return 5;    // "true" or "false"
      case RQ_TINYINT:    return 4;    // -128 to 127
      case RQ_SMALLINT:   return 6;    // -32768 to 32767
      case RQ_BIGINT:     return 20;   // -2^63 to 2^63-1
      case RQ_FLOAT:      return 25;   // Scientific notation, e.g., -1.2345678E123
      case RQ_REAL:       return 25;   // Alias for FLOAT, scientific notation
      case RQ_DOUBLE:     return 25;   // Scientific notation, e.g., -1.234567890123456E123
      case RQ_VARCHAR:    return 255;  // Arbitrary, conservative default
      case RQ_DATE:       return 10;   // "YYYY-MM-DD"
      case RQ_TIME:       return 8;    // "HH:MM:SS"
      case RQ_TIMESTAMP:  return 19;   // "YYYY-MM-DD HH:MM:SS"
      case RQ_DATALINK:   return 255;  // URL, conservative default
      case RQ_CLOB:       return 255;  // Large text, conservative default
      case RQ_NCLOB:      return 255;  // Large national text
      case RQ_NVARCHAR:   return 255;  // National text, conservative default
      case RQ_BLOB:       return 255;  // Binary data, conservative default
      default:            return 4;    // Fallback for unknown types
    }
  }

  public static boolean isSelect(String rawSql) {
    if (rawSql == null || rawSql.trim().isEmpty()) {
      return false;
    }
    return rawSql.toUpperCase().contains("SELECT");
  }

  public static String quote(String val) {
    return val.replace("'", "''");
  }

  public static L4Statement[] split(String rawSql) {
    if (rawSql == null) {
      throw new IllegalArgumentException("SQL string cannot be null");
    }
    rawSql = rawSql.trim();
    if (rawSql.isEmpty()) {
      return new L4Statement[0];
    }

    var statements = new ArrayList<String>();
    var currentStatement = new StringBuilder();
    var inSingleQuote = false;
    var inDoubleQuote = false;
    var inSingleLineComment = false;
    var inMultiLineComment = false;

    for (int i = 0; i < rawSql.length(); i++) {
      var c = rawSql.charAt(i);
      if (inSingleLineComment) {
        if (c == '\n') {
          inSingleLineComment = false;
        }
        currentStatement.append(c);
        continue;
      }
      if (inMultiLineComment) {
        currentStatement.append(c);
        if (c == '*' && i + 1 < rawSql.length() && rawSql.charAt(i + 1) == '/') {
          inMultiLineComment = false;
          currentStatement.append('/');
          i++;
        }
        continue;
      }
      if (inSingleQuote) {
        currentStatement.append(c);
        if (c == '\'') {
          inSingleQuote = false;
        }
        continue;
      }
      if (c == '\'' && !inDoubleQuote) {
        if (inSingleQuote && i + 1 < rawSql.length() && rawSql.charAt(i + 1) == '\'') {
          currentStatement.append(c);
          currentStatement.append('\'');
          i++;
          continue;
        }
        inSingleQuote = !inSingleQuote;
        currentStatement.append(c);
        continue;
      }
      if (inDoubleQuote) {
        currentStatement.append(c);
        if (c == '"') {
          inDoubleQuote = false;
        }
        continue;
      }
      if (c == '"' && !inSingleQuote) {
        inDoubleQuote = true;
        currentStatement.append(c);
        continue;
      }
      if (c == '-' && i + 1 < rawSql.length() && rawSql.charAt(i + 1) == '-') {
        inSingleLineComment = true;
        currentStatement.append(c);
        currentStatement.append('-');
        i++;
        continue;
      }
      if (c == '/' && i + 1 < rawSql.length() && rawSql.charAt(i + 1) == '*') {
        inMultiLineComment = true;
        currentStatement.append(c);
        currentStatement.append('*');
        i++;
        continue;
      }
      if (c == ';') {
        var stmt = currentStatement.toString().trim();
        if (!stmt.isEmpty()) {
          statements.add(stmt);
        }
        currentStatement = new StringBuilder();
        continue;
      }
      currentStatement.append(c);
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
