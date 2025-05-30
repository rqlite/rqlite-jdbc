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
    SqlStateInvalidColumn       = "22003",
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

  public static SQLException badBoolean(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid boolean format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badInteger(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid integer format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badLong(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid long format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badFloat(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid float format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badDouble(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid double format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badByte(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid byte format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badShort(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid short format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badBigDecimal(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid numeric format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badB64(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Base64 decoding error for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badDate(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid date format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badTimestamp(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid timestamp format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badTime(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid time format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
  }

  public static SQLException badUrl(int columnIndex, String value, Exception e) {
    return new SQLException(
      format("Invalid URL format for column %d: %s", columnIndex, value),
      SqlStateInvalidType, e
    );
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

  public static SQLException badStatement() {
    return new SQLException("SQL statement cannot be null or empty", SqlStateInvalidQuery);
  }

  public static SQLException badQuery(String msg) {
    return new SQLException(msg, SqlStateInvalidQuery);
  }

  public static SQLException badQuery(Exception e) {
    return new SQLException(format("Query execution failed: %s", e.getMessage()), SqlStateConnectionError, e);
  }

  public static SQLException badUpdate(Exception e) {
    return new SQLException(format("Update execution failed: %s", e.getMessage()), SqlStateConnectionError, e);
  }

  public static SQLException badBatch(Exception e) {
    return new SQLException(format("Batch execution failed: %s", e.getMessage()), SqlStateConnectionError, e);
  }

  public static SQLException badExec(Exception e) {
    return new SQLException(format("Execution failed: %s", e.getMessage()), SqlStateConnectionError, e);
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
      throw new SQLException(format("Invalid column index: [%d]", idx), "22003");
    }
  }

  public static void checkColumnLabel(String label, L4Result result) throws SQLException {
    if (label == null || !result.columns.contains(label)) {
      throw badColumn(label);
    }
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
