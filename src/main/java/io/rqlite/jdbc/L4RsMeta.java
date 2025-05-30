package io.rqlite.jdbc;

import io.rqlite.client.L4Result;
import java.sql.*;
import java.util.Objects;

import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;

public class L4RsMeta implements ResultSetMetaData {

  private final L4Result result;

  public L4RsMeta(L4Result result) {
    this.result = Objects.requireNonNull(result);
  }

  @Override public int getColumnCount() {
    return result.columns.size();
  }

  /*
   * rqlite does not provide direct metadata to determine if a column is auto-incrementing.
   * A proper implementation would require querying the schema (e.g., PRAGMA table_info)
   * and checking if the column is an INTEGER PRIMARY KEY with AUTOINCREMENT.
   * For now, return false to indicate no columns are auto-incrementing.
   * This may cause applications to attempt to insert values into auto-increment columns,
   * which will fail if the column is an INTEGER PRIMARY KEY without AUTOINCREMENT.
   */
  @Override public boolean isAutoIncrement(int column) throws SQLException {
    checkColumn(column, result);
    return false;
  }

  /*
   * rqlite (SQLite) is generally case-insensitive for string comparisons by default (NOCASE collation).
   * Case sensitivity can be enabled with a COLLATE BINARY clause, but rqlite does not expose
   * collation metadata in query results. A proper implementation would require querying the schema
   * (e.g., SELECT sql FROM sqlite_master)and parsing the CREATE TABLE statement
   * to check for COLLATE clauses. For now, assume case-insensitive behavior for strings (the SQLite default) and return false.
   * This may cause issues if a column uses a case-sensitive collation (e.g., COLLATE BINARY),
   * as applications might assume case-insensitive comparisons.
   */
  @Override public boolean isCaseSensitive(int column) throws SQLException {
    checkColumn(column, result);
    return false;
  }

  /* SQLite/rqlite columns are generally searchable in WHERE clauses. */
  @Override public boolean isSearchable(int column) throws SQLException {
    checkColumn(column, result);
    return true;
  }

  /* SQLite/rqlite has no dedicated currency type or metadata. */
  @Override public boolean isCurrency(int column) throws SQLException {
    checkColumn(column, result);
    return false;
  }

  /* Nullability is unknown without schema metadata. */
  @Override public int isNullable(int column) throws SQLException {
    checkColumn(column, result);
    return ResultSetMetaData.columnNullableUnknown;
  }

  @Override public boolean isSigned(int column) throws SQLException {
    checkColumn(column, result);
    var type = result.types.get(column - 1);
    return getJdbcTypeSigned(type);
  }

  @Override public int getColumnDisplaySize(int column) throws SQLException {
    checkColumn(column, result);
    var type = result.types.get(column - 1);
    if (type == null || RQ_NULL.equalsIgnoreCase(type)) {
      return 4; // "NULL"
    }
    return getJdbcTypeColumnDisplaySize(type);
  }

  @Override public String getColumnLabel(int column) throws SQLException {
    checkColumn(column, result);
    return this.result.columns.get(column - 1);
  }

  /* Fallback: returns alias or name due to lack of schema metadata. */
  @Override public String getColumnName(int column) throws SQLException {
    checkColumn(column, result);
    return this.result.columns.get(column - 1);
  }

  /* SQLite/rqlite has no schema namespaces; empty string is standard. */
  @Override public String getSchemaName(int column) throws SQLException {
    checkColumn(column, result);
    return "";
  }

  @Override public int getPrecision(int column) throws SQLException {
    checkColumn(column, result);
    return getJdbcTypePrecision(result.types.get(column - 1));
  }

  /* SQLite types have no fixed decimal places. */
  @Override public int getScale(int column) throws SQLException {
    checkColumn(column, result);
    return 0;
  }

  /* Table name unknown without query parsing or schema metadata. */
  @Override public String getTableName(int column) throws SQLException {
    checkColumn(column, result);
    return "";
  }

  /* SQLite/rqlite has no catalogs; empty string is standard. */
  @Override public String getCatalogName(int column) throws SQLException {
    checkColumn(column, result);
    return "";
  }

  @Override public int getColumnType(int column) throws SQLException {
    checkColumn(column, result);
    var type = result.types.get(column - 1);
    if (type == null) {
      return Types.NULL; // Handle NULL columns or missing type info
    }
    var typeUpper = type.toUpperCase();
    var jt = getJdbcType(typeUpper);
    if (jt == -1) {
      throw badRqLiteColumn(column, type);
    }
    return jt;
  }

  /* Return native type name */
  @Override public String getColumnTypeName(int column) throws SQLException {
    checkColumn(column, result);
    var type = result.types.get(column - 1);
    return type != null ? type : "";
  }

  /* Assume columns might be writable without metadata. */
  @Override public boolean isReadOnly(int column) throws SQLException {
    checkColumn(column, result);
    return false;
  }

  /* Assume columns are potentially writable, consistent with isReadOnly = false. */
  @Override public boolean isWritable(int column) throws SQLException {
    checkColumn(column, result);
    return true;
  }

  /* Cannot guarantee write without metadata. */
  @Override public boolean isDefinitelyWritable(int column) throws SQLException {
    checkColumn(column, result);
    return false;
  }

  @Override public String getColumnClassName(int column) throws SQLException {
    checkColumn(column, result);
    var type = result.types.get(column - 1);
    if (type == null || RQ_NULL.equalsIgnoreCase(type)) {
      return Object.class.getCanonicalName(); // For NULL columns
    }
    return getJdbcTypeClassName(type);
  }

  /* Support ResultSetMetaData and Wrapper. */
  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface == ResultSetMetaData.class || iface == Wrapper.class) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface == ResultSetMetaData.class || iface == Wrapper.class;
  }

}
