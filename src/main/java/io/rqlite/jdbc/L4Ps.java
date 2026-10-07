package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Response;
import io.rqlite.client.L4Statement;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.sql.Date;
import java.util.*;

import static io.rqlite.client.L4Err.*;
import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;

public class L4Ps extends L4St implements PreparedStatement {

  private L4Statement statement;
  private boolean resultSetAvailable = false;

  public L4Ps(L4Client client, L4Conn conn, String sql) throws SQLException {
    super(client, conn);
    if (sql == null || sql.trim().isEmpty()) {
      throw badStatement();
    }
    this.statement = new L4Statement().sql(sql);
  }

  public L4Ps(L4Client client, String sql) throws SQLException {
    this(client, null, sql);
  }

  protected void checkClosed() throws SQLException {
    if (isClosed) {
      throw stClosed(true);
    }
  }

  private static String readChars(Reader reader, long length) throws IOException {
    var sb = new StringBuilder();
    var buf = new char[8192];
    long remaining = length;
    while (remaining > 0) {
      int n = reader.read(buf, 0, (int) Math.min(buf.length, remaining));
      if (n == -1) {
        break;
      }
      sb.append(buf, 0, n);
      remaining -= n;
    }
    return sb.toString();
  }

  private void executeInternal(L4Block<L4Response> runner) throws SQLException {
    checkClosed();
    closeCurrentResultSet();
    currentResultIndex = -1;
    try {
      currentResponse = runner.get();
      var result = checkResult(currentResponse.first());
      currentResultIndex = 0;
      resultSetAvailable = result.columns != null && !result.columns.isEmpty();
      if (resultSetAvailable) {
        currentResultSet = new L4Rs(result, this).clampTo(maxRows);
        if (closeOnCompletion) {
          isClosed = true;
        }
      }
    } catch (Exception e) {
      throw badExec(e);
    }
  }

  @Override public ResultSet executeQuery() throws SQLException {
    checkClosed();
    executeInternal(() -> client.query(statement));
    if (!resultSetAvailable) {
      throw generalError("No result set returned");
    }
    return currentResultSet;
  }

  @Override public int executeUpdate() throws SQLException {
    checkClosed();
    if (isSelect(statement.sql)) {
      throw generalError("Statement is a query");
    }
    executeInternal(() -> client.execute(isAutoCommit(), statement));
    var result = currentResponse.first();
    return result.rowsAffected != null ? result.rowsAffected : 0;
  }

  @Override public boolean execute() throws SQLException {
    checkClosed();
    executeInternal(() -> {
      if (client.isBuffering()) {
        return client.execute(false, statement);
      } else if (client.getOptions().queue) {
        return isSelect(statement.sql) ? client.query(statement) : client.execute(true, statement);
      }
      return client.request(statement);
    });
    return resultSetAvailable;
  }

  @Override public void addBatch() throws SQLException {
    checkClosed();
    batch.add(statement);
    statement = new L4Statement().sql(statement.sql);
  }

  @Override public int[] executeBatch() throws SQLException {
    checkClosed();
    if (batch.isEmpty()) {
      return new int[0];
    }
    var maxBatch = client.getOptions().maxBatchStatements;
    if (maxBatch > 0 && batch.size() > maxBatch) {
      throw badParam("Batch size [" + batch.size() + "] exceeds maxBatchStatements [" + maxBatch + "]");
    }
    var size = batch.size();
    try {
      currentResponse = client.execute(isAutoCommit(), batch.toArray(new L4Statement[0]));
      var updateCounts = new int[currentResponse.results.size()];
      for (int i = 0; i < currentResponse.results.size(); i++) {
        var result = currentResponse.results.get(i);
        if (result.error != null) {
          throw new BatchUpdateException(result.error, SqlStateConnectionError, 0, updateCounts, null);
        }
        updateCounts[i] = result.rowsAffected != null ? result.rowsAffected : 0;
      }
      batch.clear();
      return updateCounts;
    } catch (Exception e) {
      var counts = new int[size];
      Arrays.fill(counts, EXECUTE_FAILED);
      batch.clear();
      throw new BatchUpdateException("Batch execution failed", SqlStateConnectionError, 0, counts, e);
    }
  }

  @Override public void clearBatch() throws SQLException {
    checkClosed();
    batch.clear();
  }

  @Override public void setNull(int parameterIndex, int sqlType) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, null);
  }

  @Override public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setByte(int parameterIndex, byte x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setShort(int parameterIndex, short x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setInt(int parameterIndex, int x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setLong(int parameterIndex, long x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setFloat(int parameterIndex, float x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setDouble(int parameterIndex, double x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x != null ? x.toString() : null);
  }

  @Override public void setString(int parameterIndex, String x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setDate(int parameterIndex, Date x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.DATE);
      return;
    }
    var utcDate = L4Utc.utcOf(x);
    statement.withPositionalParam(parameterIndex - 1, utcDate.toString()); // Format: YYYY-MM-DD
  }

  @Override public void setTime(int parameterIndex, Time x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.TIME);
      return;
    }
    var utcTime = L4Utc.utcOf(x);
    statement.withPositionalParam(parameterIndex - 1, utcTime.toString()); // Format: HH:MM:SS
  }

  @Override public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.TIMESTAMP);
      return;
    }
    statement.withPositionalParam(parameterIndex - 1, L4Utc.utcFmtOf(x));
  }

  @Override public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      var bytes = x.readNBytes(length);
      statement.withPositionalParam(parameterIndex - 1, new String(bytes, StandardCharsets.US_ASCII));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      var bytes = x.readNBytes(length);
      statement.withPositionalParam(parameterIndex - 1, new String(bytes, StandardCharsets.UTF_16));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = x.readNBytes(length);
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void clearParameters() throws SQLException {
    checkClosed();
    statement.withPositionalParams();
  }

  @Override public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, convertParameter(x, targetSqlType));
  }

  @Override public void setObject(int parameterIndex, Object x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x);
  }

  @Override public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      statement.withPositionalParam(parameterIndex - 1, readChars(reader, length));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setRef(int parameterIndex, Ref x) throws SQLException {
    checkClosed();
    throw notSupported("REF type");
  }

  @Override public void setBlob(int parameterIndex, Blob x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = x.getBytes(1, (int) x.length());
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (SQLException e) {
      throw badParam(e);
    }
  }

  @Override public void setClob(int parameterIndex, Clob x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.CLOB);
      return;
    }
    try {
      var chars = x.getSubString(1, (int) x.length());
      statement.withPositionalParam(parameterIndex - 1, chars);
    } catch (SQLException e) {
      throw badParam(e);
    }
  }

  @Override public void setArray(int parameterIndex, Array x) throws SQLException {
    checkClosed();
    throw notSupported("ARRAY type");
  }

  @Override public ResultSetMetaData getMetaData() throws SQLException {
    checkClosed();
    try {
      // Probe with NULLs for every positional placeholder so metadata can be obtained
      // before the caller has bound parameters (rqlite validates placeholder arity).
      var count = positionalParameterCount(scanPlaceholders(statement.sql));
      var probe = new L4Statement().sql(statement.sql);
      for (int i = 0; i < count; i++) {
        probe.withPositionalParam(i, null);
      }
      return new L4RsMeta(checkResult(client.query(probe).first()));
    } catch (Exception e) {
      throw badQuery(e);
    }
  }

  @Override public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.DATE);
      return;
    }
    var utcDate = L4Utc.utcOf(x);
    statement.withPositionalParam(parameterIndex - 1, utcDate.toString()); // Format: YYYY-MM-DD
  }

  @Override public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.TIME);
      return;
    }
    var utcTime = L4Utc.utcOf(x);
    statement.withPositionalParam(parameterIndex - 1, utcTime.toString()); // Format: HH:MM:SS
  }

  @Override public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.TIMESTAMP);
      return;
    }
    statement.withPositionalParam(parameterIndex - 1, L4Utc.utcFmtOf(x));
  }

  @Override public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, null);
  }

  @Override public void setURL(int parameterIndex, URL x) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, x != null ? x.toString() : null);
  }

  @Override public ParameterMetaData getParameterMetaData() throws SQLException {
    checkClosed();
    return new L4PsPm(client, statement);
  }

  @Override public void setRowId(int parameterIndex, RowId x) throws SQLException {
    checkClosed();
    throw notSupported("RowId type");
  }

  @Override public void setNString(int parameterIndex, String value) throws SQLException {
    checkClosed();
    statement.withPositionalParam(parameterIndex - 1, value);
  }

  @Override public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
    checkClosed();
    if (value == null) {
      setNull(parameterIndex, Types.NVARCHAR);
      return;
    }
    try {
      statement.withPositionalParam(parameterIndex - 1, readChars(value, length));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setNClob(int parameterIndex, NClob value) throws SQLException {
    checkClosed();
    if (value == null) {
      setNull(parameterIndex, Types.NCLOB);
      return;
    }
    try {
      var chars = value.getSubString(1, (int) value.length());
      statement.withPositionalParam(parameterIndex - 1, chars);
    } catch (SQLException e) {
      throw badParam(e);
    }
  }

  @Override public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.CLOB);
      return;
    }
    try {
      statement.withPositionalParam(parameterIndex - 1, readChars(reader, length));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
    checkClosed();
    if (inputStream == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = inputStream.readNBytes((int) length);
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.NCLOB);
      return;
    }
    try {
      statement.withPositionalParam(parameterIndex - 1, readChars(reader, length));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
    checkClosed();
    throw notSupported("SQLXML type");
  }

  @Override public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
    checkClosed();
    var converted = convertParameter(x, targetSqlType);
    if (converted instanceof BigDecimal && scaleOrLength >= 0) {
      converted = ((BigDecimal) converted).setScale(scaleOrLength, RoundingMode.HALF_UP);
    }
    statement.withPositionalParam(parameterIndex - 1, converted);
  }

  @Override public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      var bytes = x.readNBytes((int) length);
      statement.withPositionalParam(parameterIndex - 1, new String(bytes, StandardCharsets.US_ASCII));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = x.readNBytes((int) length);
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      statement.withPositionalParam(parameterIndex - 1, readChars(reader, length));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      var bytes = x.readAllBytes();
      statement.withPositionalParam(parameterIndex - 1, new String(bytes, StandardCharsets.US_ASCII));
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
    checkClosed();
    if (x == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = x.readAllBytes();
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.VARCHAR);
      return;
    }
    try {
      var writer = new StringWriter();
      reader.transferTo(writer);
      statement.withPositionalParam(parameterIndex - 1, writer.toString());
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
    checkClosed();
    if (value == null) {
      setNull(parameterIndex, Types.NVARCHAR);
      return;
    }
    try {
      var writer = new StringWriter();
      value.transferTo(writer);
      statement.withPositionalParam(parameterIndex - 1, writer.toString());
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setClob(int parameterIndex, Reader reader) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.CLOB);
      return;
    }
    try {
      var writer = new StringWriter();
      reader.transferTo(writer);
      statement.withPositionalParam(parameterIndex - 1, writer.toString());
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
    checkClosed();
    if (inputStream == null) {
      setNull(parameterIndex, Types.BLOB);
      return;
    }
    try {
      var bytes = inputStream.readAllBytes();
      statement.withPositionalParam(parameterIndex - 1, bytes);
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public void setNClob(int parameterIndex, Reader reader) throws SQLException {
    checkClosed();
    if (reader == null) {
      setNull(parameterIndex, Types.NCLOB);
      return;
    }
    try {
      var writer = new StringWriter();
      reader.transferTo(writer);
      statement.withPositionalParam(parameterIndex - 1, writer.toString());
    } catch (IOException e) {
      throw badParam(e);
    }
  }

  @Override public ResultSet executeQuery(String sql) throws SQLException {
    throw badQuery("Use executeQuery() without parameters for PreparedStatement");
  }

  @Override public int executeUpdate(String sql) throws SQLException {
    throw badQuery("Use executeUpdate() without parameters for PreparedStatement");
  }

  @Override public boolean execute(String sql) throws SQLException {
    throw badQuery("Use execute() without parameters for PreparedStatement");
  }

  @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    throw badQuery("Use executeUpdate() without parameters for PreparedStatement");
  }

  @Override public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    throw badQuery("Use executeUpdate() without parameters for PreparedStatement");
  }

  @Override public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    throw badQuery("Use executeUpdate() without parameters for PreparedStatement");
  }

  @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    throw badQuery("Use execute() without parameters for PreparedStatement");
  }

  @Override public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    throw badQuery("Use execute() without parameters for PreparedStatement");
  }

  @Override public boolean execute(String sql, String[] columnNames) throws SQLException {
    throw badQuery("Use execute() without parameters for PreparedStatement");
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface.isAssignableFrom(getClass())) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface.isAssignableFrom(getClass());
  }

}
