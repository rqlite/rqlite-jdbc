package io.rqlite.jdbc;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Single source of truth for the mapping between rqlite/SQLite declared types and JDBC types,
 * replacing the parallel switches previously spread across {@link L4Jdbc}.
 */
public enum RqType {

  INTEGER("INTEGER", Types.INTEGER, 10, 11, Integer.class, true),
  INT("INT", Types.INTEGER, 10, 11, Integer.class, true),
  NUMERIC("NUMERIC", Types.NUMERIC, 38, 38, BigDecimal.class, true),
  DECIMAL("DECIMAL", Types.NUMERIC, 38, 38, BigDecimal.class, true),
  BOOLEAN("BOOLEAN", Types.BOOLEAN, 1, 5, Boolean.class, false),
  TINYINT("TINYINT", Types.TINYINT, 3, 4, Byte.class, true),
  SMALLINT("SMALLINT", Types.SMALLINT, 5, 6, Short.class, true),
  BIGINT("BIGINT", Types.BIGINT, 19, 20, Long.class, true),
  FLOAT("FLOAT", Types.FLOAT, 7, 25, Float.class, true),
  REAL("REAL", Types.FLOAT, 7, 25, Float.class, true),
  DOUBLE("DOUBLE", Types.DOUBLE, 15, 25, Double.class, true),
  TEXT("TEXT", Types.VARCHAR, 255, 255, String.class, false),
  VARCHAR("VARCHAR", Types.VARCHAR, 255, 255, String.class, false),
  DATE("DATE", Types.DATE, 10, 10, java.sql.Date.class, false),
  TIME("TIME", Types.TIME, 8, 8, java.sql.Time.class, false),
  TIMESTAMP("TIMESTAMP", Types.TIMESTAMP, 19, 19, java.sql.Timestamp.class, false),
  DATETIME("DATETIME", Types.TIMESTAMP, 19, 19, java.sql.Timestamp.class, false),
  DATALINK("DATALINK", Types.DATALINK, 255, 255, URL.class, false),
  CLOB("CLOB", Types.CLOB, 65535, 255, java.sql.Clob.class, false),
  NCLOB("NCLOB", Types.NCLOB, 65535, 255, java.sql.NClob.class, false),
  NVARCHAR("NVARCHAR", Types.NVARCHAR, 255, 255, String.class, false),
  BLOB("BLOB", Types.BLOB, 65535, 255, byte[].class, false),
  NULL("NULL", Types.NULL, 0, 4, Object.class, false);

  private static final Map<String, RqType> BY_NAME = new HashMap<>();
  private static final Map<Class<?>, RqType> BY_CLASS = new HashMap<>();

  static {
    for (var t : values()) {
      BY_NAME.put(t.name(), t);
    }
    // Inverse mapping uses the canonical type for each Java class.
    BY_CLASS.put(Integer.class, INTEGER);
    BY_CLASS.put(BigDecimal.class, NUMERIC);
    BY_CLASS.put(Boolean.class, BOOLEAN);
    BY_CLASS.put(Byte.class, TINYINT);
    BY_CLASS.put(Short.class, SMALLINT);
    BY_CLASS.put(Long.class, BIGINT);
    BY_CLASS.put(Float.class, FLOAT);
    BY_CLASS.put(Double.class, DOUBLE);
    BY_CLASS.put(String.class, VARCHAR);
    BY_CLASS.put(java.sql.Date.class, DATE);
    BY_CLASS.put(java.sql.Time.class, TIME);
    BY_CLASS.put(java.sql.Timestamp.class, TIMESTAMP);
    BY_CLASS.put(URL.class, DATALINK);
    BY_CLASS.put(java.sql.Clob.class, CLOB);
    BY_CLASS.put(java.sql.NClob.class, NCLOB);
    BY_CLASS.put(byte[].class, BLOB);
  }

  public final int jdbcType;
  public final int precision;
  public final int displaySize;
  public final Class<?> javaClass;
  public final boolean signed;

  RqType(String name, int jdbcType, int precision, int displaySize, Class<?> javaClass, boolean signed) {
    this.jdbcType = jdbcType;
    this.precision = precision;
    this.displaySize = displaySize;
    this.javaClass = javaClass;
    this.signed = signed;
  }

  /** Looks up a type by its normalized base name (e.g. {@code VARCHAR}); null when unknown. */
  public static RqType fromBase(String base) {
    return base == null ? null : BY_NAME.get(base);
  }

  /** Looks up the canonical type for a Java class; null when unmapped. */
  public static RqType fromClass(Class<?> clazz) {
    return clazz == null ? null : BY_CLASS.get(clazz);
  }

}
