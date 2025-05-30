package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Response;
import io.rqlite.client.L4Statement;

import java.sql.*;
import java.util.*;

import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.client.L4Err.*;

public class L4St implements Statement {

  protected final L4Conn            conn;
  protected final L4Client client;
  protected final List<L4Statement> batch = new ArrayList<>();

  protected boolean                 isClosed = false;
  protected L4Rs                    currentResultSet = null;
  protected L4Response currentResponse = null;
  protected int                     maxRows = -1;
  protected int                     fetchSize = 0;
  protected boolean                 closeOnCompletion = false;
  protected int                     currentResultIndex = -1;

  public L4St(L4Client client, L4Conn conn) {
    this.client = Objects.requireNonNull(client);
    this.conn = conn;
  }

  public L4St(L4Client client) {
    this(client, null);
  }

  protected boolean isAutoCommit() {
    return conn != null && conn.autoCommit;
  }

  protected void checkClosed() throws SQLException {
    if (isClosed) {
      throw stClosed(false);
    }
  }

  protected void closeCurrentResultSet() throws SQLException {
    if (currentResultSet != null && !currentResultSet.isClosed()) {
      currentResultSet.close();
    }
    currentResultSet = null;
  }

  private L4Response runRaw(String sql) {
    var sel = isSelect(sql);
    var sta = split(sql);
    var res = sel ? client.query(sta) : client.execute(isAutoCommit(), sta);
    for (var result : res.results) {
      checkResult(result);
    }
    return res;
  }

  @Override public ResultSet executeQuery(String sql) throws SQLException {
    checkClosed();
    closeCurrentResultSet();
    currentResultIndex = -1;
    if (sql == null || sql.trim().isEmpty()) {
      throw badStatement();
    }
    try {
      currentResponse = runRaw(sql);
      currentResultIndex = 0;
      currentResultSet = new L4Rs(currentResponse.first(), this).clampTo(maxRows);
      return currentResultSet;
    } catch (Exception e) {
      throw badQuery(e);
    }
  }

  @Override public int executeUpdate(String sql) throws SQLException {
    checkClosed();
    closeCurrentResultSet();
    currentResultIndex = -1;
    if (sql == null || sql.trim().isEmpty()) {
      throw badStatement();
    }
    try {
      currentResponse = client.execute(isAutoCommit(), new L4Statement().sql(sql));
      var result = checkResult(currentResponse.first());
      return result.rowsAffected != null ? result.rowsAffected : 0;
    } catch (Exception e) {
      throw badUpdate(e);
    }
  }

  @Override public void close() throws SQLException {
    if (!isClosed) {
      closeCurrentResultSet();
      batch.clear();
      currentResultIndex = -1;
      currentResponse = null;
      closeOnCompletion = false;
      isClosed = true;
    }
  }

  @Override public int getMaxFieldSize() throws SQLException {
    checkClosed();
    throw notSupported("Maximum field size");
  }

  @Override public void setMaxFieldSize(int max) throws SQLException {
    checkClosed();
    throw notSupported("Maximum field size");
  }

  @Override public int getMaxRows() throws SQLException {
    checkClosed();
    return maxRows;
  }

  @Override public void setMaxRows(int max) throws SQLException {
    checkClosed();
    if (max < 0) {
      throw badMaxRows();
    }
    this.maxRows = max;
  }

  @Override public void setEscapeProcessing(boolean enable) throws SQLException {
    checkClosed();
  }

  @Override public int getQueryTimeout() throws SQLException {
    checkClosed();
    return (int) (client.getTxTimeoutSec() == -1 ? 0 : client.getTxTimeoutSec());
  }

  @Override public void setQueryTimeout(int seconds) throws SQLException {
    checkClosed();
    try {
      client.withTxTimeoutSec(seconds);
    } catch (Exception e) {
      throw badParam(e);
    }
  }

  @Override public void cancel() throws SQLException {
    checkClosed();
    throw notSupported("Statement cancellation");
  }

  @Override public SQLWarning getWarnings() throws SQLException {
    checkClosed();
    if (currentResponse != null) {
      var root = (SQLWarning) null;
      for (var res : currentResponse.results) {
        if (res.error != null) {
          var warn = warnQuery(res.error);
          if (root == null) {
            root = warn;
          } else {
            root.setNextWarning(warn);
          }
        }
      }
      return root;
    }
    return null;
  }

  @Override public void clearWarnings() throws SQLException {
    checkClosed();
    if (currentResponse != null) {
      for (var res : currentResponse.results) {
        res.error = null;
      }
    }
  }

  @Override public void setCursorName(String name) throws SQLException {
    checkClosed();
    throw notSupported("Positioned updates via cursor name");
  }

  @Override public boolean execute(String sql) throws SQLException {
    checkClosed();
    closeCurrentResultSet();
    currentResultIndex = -1;
    if (sql == null || sql.trim().isEmpty()) {
      throw badStatement();
    }
    try {
      currentResponse = runRaw(sql);
      if (currentResponse.results.isEmpty()) {
        return false;
      }
      currentResultIndex = 0;
      var result = currentResponse.first();
      if (result.columns != null && !result.columns.isEmpty()) {
        currentResultSet = new L4Rs(result, this).clampTo(maxRows);
        return true;
      }
      return false;
    } catch (Exception e) {
      throw badExec(e);
    }
  }

  @Override public ResultSet getResultSet() throws SQLException {
    checkClosed();
    return currentResultSet;
  }

  @Override public int getUpdateCount() throws SQLException {
    checkClosed();
    if (currentResultIndex < 0 || currentResultIndex >= currentResponse.results.size()) {
      return -1;
    }
    var result = currentResponse.results.get(currentResultIndex);
    if (result.columns != null && !result.columns.isEmpty()) {
      return -1; // Indicates a ResultSet is available
    }
    return result.rowsAffected != null ? result.rowsAffected : 0;
  }

  @Override public boolean getMoreResults() throws SQLException {
    return getMoreResults(CLOSE_CURRENT_RESULT);
  }

  @Override public void setFetchDirection(int direction) throws SQLException {
    checkClosed();
    throw notSupported("Fetch direction (scrollable result sets)");
  }

  @Override public int getFetchDirection() throws SQLException {
    checkClosed();
    throw notSupported("Fetch direction (scrollable result sets)");
  }

  @Override public void setFetchSize(int rows) throws SQLException {
    checkClosed();
    if (rows < 0) {
      throw badFetchSize(rows);
    }
    this.fetchSize = rows;
  }

  @Override public int getFetchSize() throws SQLException {
    checkClosed();
    return fetchSize;
  }

  @Override public int getResultSetConcurrency() throws SQLException {
    checkClosed();
    return ResultSet.CONCUR_READ_ONLY;
  }

  @Override public int getResultSetType() throws SQLException {
    checkClosed();
    return ResultSet.TYPE_FORWARD_ONLY;
  }

  @Override public void addBatch(String sql) throws SQLException {
    checkClosed();
    if (sql == null || sql.trim().isEmpty()) {
      throw badStatement();
    }
    batch.add(new L4Statement().sql(sql));
  }

  @Override public void clearBatch() throws SQLException {
    checkClosed();
    batch.clear();
  }

  @Override public int[] executeBatch() throws SQLException {
    checkClosed();
    closeCurrentResultSet();
    if (batch.isEmpty()) {
      return new int[0];
    }
    try {
      currentResponse = client.execute(isAutoCommit(), batch.toArray(new L4Statement[0]));
      var updateCounts = new int[currentResponse.results.size()];
      for (int i = 0; i < currentResponse.results.size(); i++) {
        var result = currentResponse.results.get(i);
        if (result.error != null) {
          throw new BatchUpdateException(result.error, SqlStateGeneralError, updateCounts, null);
        }
        updateCounts[i] = result.rowsAffected;
      }
      batch.clear();
      return updateCounts;
    } catch (Exception e) {
      throw badBatch(e);
    }
  }

  @Override public Connection getConnection() {
    return null;
  }

  @Override public boolean getMoreResults(int current) throws SQLException {
    checkClosed();
    if (current != CLOSE_CURRENT_RESULT) {
      throw notSupported("Result handling modes other than CLOSE_CURRENT_RESULT");
    }
    closeCurrentResultSet();
    if (currentResultIndex + 1 < currentResponse.results.size()) {
      currentResultIndex++;
      var result = currentResponse.results.get(currentResultIndex);
      if (result.error != null) {
        throw generalError(result.error);
      }
      if (result.columns != null && !result.columns.isEmpty()) {
        currentResultSet = new L4Rs(result, this).clampTo(maxRows);
        return true;
      }
      return false;
    }
    currentResultIndex = currentResponse.results.size();
    return false;
  }

  @Override public ResultSet getGeneratedKeys() throws SQLException {
    checkClosed();
    throw notSupported("Generated keys");
  }

  @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
    checkClosed();
    if (autoGeneratedKeys == RETURN_GENERATED_KEYS) {
      throw notSupported("Generated keys");
    }
    return executeUpdate(sql);
  }

  @Override public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
    checkClosed();
    throw notSupported("Generated keys by column indexes");
  }

  @Override public int executeUpdate(String sql, String[] columnNames) throws SQLException {
    checkClosed();
    throw notSupported("Generated keys by column names");
  }

  @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
    checkClosed();
    if (autoGeneratedKeys == RETURN_GENERATED_KEYS) {
      throw notSupported("Generated keys");
    }
    return execute(sql);
  }

  @Override public boolean execute(String sql, int[] columnIndexes) throws SQLException {
    checkClosed();
    throw notSupported("Generated keys by column indexes");
  }

  @Override public boolean execute(String sql, String[] columnNames) throws SQLException {
    checkClosed();
    throw notSupported("Generated keys by column names");
  }

  @Override public int getResultSetHoldability() throws SQLException {
    checkClosed();
    return ResultSet.CLOSE_CURSORS_AT_COMMIT;
  }

  @Override public boolean isClosed() {
    return isClosed;
  }

  @Override public void setPoolable(boolean poolable) throws SQLException {
    checkClosed();
    throw notSupported("Statement pooling");
  }

  @Override public boolean isPoolable() throws SQLException {
    checkClosed();
    throw notSupported("Statement pooling");
  }

  @Override public void closeOnCompletion() throws SQLException {
    checkClosed();
    closeOnCompletion = true;
  }

  @Override public boolean isCloseOnCompletion() throws SQLException {
    checkClosed();
    return closeOnCompletion;
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface == Statement.class || iface == Wrapper.class) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface == Statement.class || iface == Wrapper.class;
  }

}
