package io.rqlite.jdbc;

import io.rqlite.client.L4Result;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;

import static java.lang.String.format;

public class L4Err {

  public static final String
    SqlStateInvalidParam        = "22003",
    SqlStateInvalidConversion   = "22018",
    SqlStateGeneralError        = "HY000",
    SqlStateInvalidColumn       = "42S22",
    SqlStateInvalidCursor       = "24000",
    SqlStateFeatureNotSupported = "0A000",
    SqlStateInvalidAttr         = "HY092",
    SqlStateInvalidType         = "22005",
    SqlStateInvalidQuery        = "42000",
    SqlStateConnectionError     = "08S01",
    SqlStateInvalidTransaction  = "25000";

  public static SQLException generalError(String msg) {
    return new SQLException(msg, SqlStateGeneralError);
  }

  public static SQLException notSupported(String feature) {
    return new SQLFeatureNotSupportedException(
      format("%s not supported", feature), SqlStateFeatureNotSupported
    );
  }

  public static SQLException rangeError(String value, int columnIndex, int jdbcType) {
    return new SQLException(
      format("Value [%s] out of range for JDBC type [%d] in column %d", value, jdbcType, columnIndex),
      SqlStateInvalidType
    );
  }

  public static SQLException castError(String value, int columnIndex, int sourceJdbcType, int targetJdbcType) {
    return new SQLException(
      format(
        "Cannot convert value [%s], column %d (type %d) to (type %d)",
        value, columnIndex, sourceJdbcType, targetJdbcType
      ),
      SqlStateInvalidConversion
    );
  }

  private static SQLException badFormat(String noun, int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid %s format for column %d: %s", noun, columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badBoolean(int columnIndex, String value, Exception e) {
    return badFormat("boolean", columnIndex, value, e);
  }

  public static SQLException badInteger(int columnIndex, String value, Exception e) {
    return badFormat("integer", columnIndex, value, e);
  }

  public static SQLException badLong(int columnIndex, String value, Exception e) {
    return badFormat("long", columnIndex, value, e);
  }

  public static SQLException badFloat(int columnIndex, String value, Exception e) {
    return badFormat("float", columnIndex, value, e);
  }

  public static SQLException badDouble(int columnIndex, String value, Exception e) {
    return badFormat("double", columnIndex, value, e);
  }

  public static SQLException badByte(int columnIndex, String value, Exception e) {
    return badFormat("byte", columnIndex, value, e);
  }

  public static SQLException badShort(int columnIndex, String value, Exception e) {
    return badFormat("short", columnIndex, value, e);
  }

  public static SQLException badBigDecimal(int columnIndex, String value, Exception e) {
    return badFormat("numeric", columnIndex, value, e);
  }

  public static SQLException badB64(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Base64 decoding error for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badDate(int columnIndex, String value, Exception e) {
    return badFormat("date", columnIndex, value, e);
  }

  public static SQLException badTimestamp(int columnIndex, String value, Exception e) {
    return badFormat("timestamp", columnIndex, value, e);
  }

  public static SQLException badTime(int columnIndex, String value, Exception e) {
    return badFormat("time", columnIndex, value, e);
  }

  public static SQLException badUrl(int columnIndex, String value, Exception e) {
    return badFormat("URL", columnIndex, value, e);
  }

  public static SQLException badType(int columnIndex, String value) {
    return new SQLException(
      format("Target type cannot be null for column [%d], value [%s]", columnIndex, value)
    );
  }

  public static SQLException badConversion(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Conversion error for column %d: %s", columnIndex, value),
      SqlStateInvalidConversion, e
    );
  }

  public static <T> SQLException badConversion(int columnIndex, int sourceJdbcType, Class<T> type) {
    return new SQLException(
      format("Cannot convert column %d (type %d) to %s", columnIndex, sourceJdbcType, type.getName()),
      SqlStateFeatureNotSupported
    );
  }

  public static SQLException badColumn(String columnLabel) {
    return new SQLException(format("Invalid column: %s", columnLabel), SqlStateInvalidColumn);
  }

  public static SQLException badFetchSize(int rows) {
    return new SQLException(format("Fetch size cannot be negative: %d", rows), SqlStateInvalidAttr);
  }

  public static SQLException badMaxRows() {
    return new SQLException("Max rows cannot be negative", SqlStateInvalidParam);
  }

  public static SQLException badRqLiteColumn(int column, String type) {
    return new SQLException(
      format("Unrecognized rqlite type: [%s] for column [%d]", type, column),
      SqlStateInvalidType
    );
  }

  public static SQLException badParam(String msg) {
    return new SQLException(msg, SqlStateInvalidParam);
  }

  public static SQLException badParam(Exception e) {
    return new SQLException(e.getMessage(), SqlStateInvalidParam, e);
  }

  public static SQLException badInterface() {
    return new SQLException("Interface cannot be null");
  }

  public static <T> SQLException badUnwrap(Class<T> iface) {
    return new SQLException(format("Cannot unwrap to [%s]", iface.getCanonicalName()));
  }

  public static <T> T unwrap(Class<T> iface, Object self) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface.isAssignableFrom(self.getClass())) {
      return iface.cast(self);
    }
    throw badUnwrap(iface);
  }

  public static boolean isWrapperFor(Class<?> iface, Object self) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface.isAssignableFrom(self.getClass());
  }

  public static SQLException badStatement() {
    return new SQLException("SQL statement cannot be null or empty", SqlStateInvalidQuery);
  }

  public static SQLException badQuery(String msg) {
    return new SQLException(msg, SqlStateInvalidQuery);
  }

  private static SQLException execFailed(String op, Exception e) {
    return new SQLException(format("%s execution failed: %s", op, e.getMessage()), SqlStateConnectionError, e);
  }

  public static SQLException badQuery(Exception e) {
    return execFailed("Query", e);
  }

  public static SQLException badUpdate(Exception e) {
    return execFailed("Update", e);
  }

  public static SQLException badBatch(Exception e) {
    return execFailed("Batch", e);
  }

  public static SQLException badExec(Exception e) {
    return execFailed("Execution", e);
  }

  public static SQLException badState(String msg) {
    return new SQLException(msg, SqlStateInvalidTransaction);
  }

  public static SQLException badState(String msg, Exception e) {
    return new SQLException(msg, e);
  }

  public static SQLException rsClosed() {
    return new SQLException("ResultSet is closed", SqlStateGeneralError);
  }

  public static SQLException stClosed(boolean prepared) {
    return new SQLException(format("%s is closed", prepared ? "Prepared statement" : "Statement"), SqlStateGeneralError);
  }

  public static SQLWarning warnQuery(String msg) {
    return new SQLWarning(msg, SqlStateInvalidQuery);
  }

  public static void checkColumn(int idx, L4Result result) throws SQLException {
    if (idx < 1 || idx > result.columns.size()) {
      throw new SQLException(format("Invalid column index: [%d]", idx), SqlStateInvalidColumn);
    }
  }

  public static void checkColumnLabel(String label, L4Result result) throws SQLException {
    if (label == null) {
      throw badColumn(label);
    }
    for (var column : result.columns) {
      if (column.equalsIgnoreCase(label)) {
        return;
      }
    }
    throw badColumn(label);
  }

  public static void checkRow(int currentRow, L4Result result, boolean isClosed) throws SQLException {
    if (isClosed) {
      throw new SQLException("ResultSet is closed", SqlStateGeneralError);
    }
    if (currentRow < 0 || currentRow >= result.values.size()) {
      throw new SQLException("Invalid row position: " + (currentRow + 1), SqlStateInvalidCursor);
    }
  }

}
