package io.rqlite.jdbc;

import io.rqlite.client.L4Result;
import java.io.*;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.util.*;

import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;
import static java.lang.String.format;

public class L4Rs implements ResultSet {

  public  final L4Result result;
  private final L4RsMeta meta;
  private final Statement statement;
  private int currentRow = -1; // Before first row
  private boolean isClosed = false;
  private boolean wasNull = false;

  public L4Rs(L4Result result, Statement statement) {
    this.result = Objects.requireNonNull(result);
    this.meta = new L4RsMeta(result);
    this.statement = statement;
  }

  private void checkClosed() throws SQLException {
    if (isClosed) {
      throw rsClosed();
    }
  }

  public L4Rs clampTo(int maxRows) {
    if (maxRows > 0 && result.values.size() > maxRows) {
      result.values = result.values.subList(0, maxRows);
    }
    return this;
  }

  @Override public boolean next() throws SQLException {
    checkClosed();
    if (currentRow + 1 < result.values.size()) {
      currentRow++;
      return true;
    }
    currentRow = result.values.size(); // After last row
    return false;
  }

  @Override public void close() throws SQLException {
    if (!isClosed) {
      isClosed = true;
      if (statement instanceof L4St) {
        if (statement.isCloseOnCompletion()) {
          statement.close();
        }
      }
    }
  }

  @Override public boolean wasNull() throws SQLException {
    checkClosed();
    return wasNull;
  }

  private Object tryCast(int columnIndex, int targetJdbcType, int scale, Calendar cal, Class<?> type) throws SQLException {
    checkClosed();
    checkRow(currentRow, result, isClosed);
    checkColumn(columnIndex, result);
    var value = result.values.get(currentRow).get(columnIndex - 1);
    wasNull = (value == null || value.equals("null"));
    if (wasNull) {
      return null;
    }
    int sourceJdbcType = meta.getColumnType(columnIndex);
    // TODO for numeric/decimal types, we need some way to retrieve the scale value.
    return convertValue(value, sourceJdbcType, targetJdbcType, columnIndex, scale, cal, type);
  }

  private Object tryCast(int columnIndex, int targetJdbcType, int scale, Calendar cal) throws SQLException {
    return tryCast(columnIndex, targetJdbcType, scale, cal, null);
  }

  private Object tryCast(int columnIndex, int targetJdbcType) throws SQLException {
    return tryCast(columnIndex, targetJdbcType, -1, null, null);
  }

  private Object tryCast(int columnIndex, Class<?> type) throws SQLException {
    return tryCast(columnIndex, OBJECT_STREAM, -1, null, type);
  }

  @Override public String getString(int columnIndex) throws SQLException {
    return (String) tryCast(columnIndex, Types.VARCHAR);
  }

  @Override public boolean getBoolean(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.BOOLEAN);
    return value != null ? (Boolean) value : false;
  }

  @Override public byte getByte(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.TINYINT);
    return value != null ? (Byte) value : 0;
  }

  @Override public short getShort(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.SMALLINT);
    return value != null ? (Short) value : 0;
  }

  @Override public int getInt(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.INTEGER);
    return value != null ? (Integer) value : 0;
  }

  @Override public long getLong(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.BIGINT);
    return value != null ? (Long) value : 0L;
  }

  @Override public float getFloat(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.FLOAT);
    return value != null ? (Float) value : 0.0f;
  }

  @Override public double getDouble(int columnIndex) throws SQLException {
    var value = tryCast(columnIndex, Types.DOUBLE);
    return value != null ? (Double) value : 0.0;
  }

  @Override public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
    return (BigDecimal) tryCast(columnIndex, Types.DECIMAL, scale, null);
  }

  @Override public byte[] getBytes(int columnIndex) throws SQLException {
    return (byte[]) tryCast(columnIndex, Types.BLOB);
  }

  @Override public Date getDate(int columnIndex) throws SQLException {
    return (Date) tryCast(columnIndex, Types.DATE);
  }

  @Override public Time getTime(int columnIndex) throws SQLException {
    return (Time) tryCast(columnIndex, Types.TIME);
  }

  @Override public Timestamp getTimestamp(int columnIndex) throws SQLException {
    return (Timestamp) tryCast(columnIndex, Types.TIMESTAMP);
  }

  @Override public InputStream getAsciiStream(int columnIndex) throws SQLException {
    return (InputStream) tryCast(columnIndex, L4Jdbc.VARCHAR_STREAM);
  }

  @Override public InputStream getUnicodeStream(int columnIndex) throws SQLException {
    return (InputStream) tryCast(columnIndex, L4Jdbc.UNICODE_STREAM);
  }

  @Override public InputStream getBinaryStream(int columnIndex) throws SQLException {
    return (InputStream) tryCast(columnIndex, L4Jdbc.BINARY_STREAM);
  }

  @Override public String getString(String columnLabel) throws SQLException {
    return getString(findColumn(columnLabel));
  }

  @Override public boolean getBoolean(String columnLabel) throws SQLException {
    return getBoolean(findColumn(columnLabel));
  }

  @Override public byte getByte(String columnLabel) throws SQLException {
    return getByte(findColumn(columnLabel));
  }

  @Override public short getShort(String columnLabel) throws SQLException {
    return getShort(findColumn(columnLabel));
  }

  @Override public int getInt(String columnLabel) throws SQLException {
    return getInt(findColumn(columnLabel));
  }

  @Override public long getLong(String columnLabel) throws SQLException {
    return getLong(findColumn(columnLabel));
  }

  @Override public float getFloat(String columnLabel) throws SQLException {
    return getFloat(findColumn(columnLabel));
  }

  @Override public double getDouble(String columnLabel) throws SQLException {
    return getDouble(findColumn(columnLabel));
  }

  @Override public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
    return getBigDecimal(findColumn(columnLabel), scale);
  }

  @Override public byte[] getBytes(String columnLabel) throws SQLException {
    return getBytes(findColumn(columnLabel));
  }

  @Override public Date getDate(String columnLabel) throws SQLException {
    return getDate(findColumn(columnLabel));
  }

  @Override public Time getTime(String columnLabel) throws SQLException {
    return getTime(findColumn(columnLabel));
  }

  @Override public Timestamp getTimestamp(String columnLabel) throws SQLException {
    return getTimestamp(findColumn(columnLabel));
  }

  @Override public InputStream getAsciiStream(String columnLabel) throws SQLException {
    return getAsciiStream(findColumn(columnLabel));
  }

  @Override public InputStream getUnicodeStream(String columnLabel) throws SQLException {
    return getUnicodeStream(findColumn(columnLabel));
  }

  @Override public InputStream getBinaryStream(String columnLabel) throws SQLException {
    return getBinaryStream(findColumn(columnLabel));
  }

  @Override public SQLWarning getWarnings() throws SQLException {
    checkClosed();
    if (result.error != null) {
      return warnQuery(result.error);
    }
    return null;
  }

  @Override public void clearWarnings() throws SQLException {
    result.error = null;
    checkClosed();
  }

  @Override public String getCursorName() throws SQLException {
    checkClosed();
    throw notSupported("Cursor names");
  }

  @Override public ResultSetMetaData getMetaData() throws SQLException {
    checkClosed();
    return meta;
  }

  @Override public Object getObject(int columnIndex) throws SQLException {
    var targetJdbcType = meta.getColumnType(columnIndex);
    return tryCast(columnIndex, targetJdbcType);
  }

  @Override public Object getObject(String columnLabel) throws SQLException {
    return getObject(findColumn(columnLabel));
  }

  @Override public int findColumn(String columnLabel) throws SQLException {
    checkClosed();
    checkColumnLabel(columnLabel, result);
    for (int i = 0; i < result.columns.size(); i++) {
      if (columnLabel.equalsIgnoreCase(result.columns.get(i))) {
        return i + 1;
      }
    }
    throw badColumn(columnLabel);
  }

  @Override public Reader getCharacterStream(int columnIndex) throws SQLException {
    return (Reader) tryCast(columnIndex, CHARACTER_STREAM);
  }

  @Override public Reader getCharacterStream(String columnLabel) throws SQLException {
    return getCharacterStream(findColumn(columnLabel));
  }

  @Override public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
    return getBigDecimal(columnIndex, -1);
  }

  @Override public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
    return getBigDecimal(findColumn(columnLabel));
  }

  @Override public boolean isBeforeFirst() throws SQLException {
    checkClosed();
    return !result.values.isEmpty() && currentRow == -1;
  }

  @Override public boolean isAfterLast() throws SQLException {
    checkClosed();
    return !result.values.isEmpty() && currentRow >= result.values.size();
  }

  @Override public boolean isFirst() throws SQLException {
    checkClosed();
    return !result.values.isEmpty() && currentRow == 0;
  }

  @Override public boolean isLast() throws SQLException {
    checkClosed();
    return !result.values.isEmpty() && currentRow == result.values.size() - 1;
  }

  private void noScrollingImpl() throws SQLException {
    checkClosed();
    throw notSupported("Scrolling for TYPE_FORWARD_ONLY");
  }

  @Override public void beforeFirst() throws SQLException {
    noScrollingImpl();
  }

  @Override public void afterLast() throws SQLException {
    noScrollingImpl();
  }

  @Override public boolean first() throws SQLException {
    noScrollingImpl();
    return false;
  }

  @Override public boolean last() throws SQLException {
    noScrollingImpl();
    return false;
  }

  @Override public int getRow() throws SQLException {
    checkClosed();
    if (result.values.isEmpty() || currentRow < 0 || currentRow >= result.values.size()) {
      return 0;
    }
    return currentRow + 1;
  }

  @Override public boolean absolute(int row) throws SQLException {
    noScrollingImpl();
    return false;
  }

  @Override public boolean relative(int rows) throws SQLException {
    noScrollingImpl();
    return false;
  }

  @Override public boolean previous() throws SQLException {
    noScrollingImpl();
    return false;
  }

  @Override public void setFetchDirection(int direction) throws SQLException {
    checkClosed();
    if (direction == ResultSet.FETCH_FORWARD) {
      return;
    }
    throw notSupported(format("Fetch direction for TYPE_FORWARD_ONLY: %d", direction));
  }

  @Override public int getFetchDirection() throws SQLException {
    checkClosed();
    return ResultSet.FETCH_FORWARD;
  }

  @Override public void setFetchSize(int rows) throws SQLException {
    checkClosed();
    if (rows < 0) {
      throw badFetchSize(rows);
    }
  }

  @Override public int getFetchSize() throws SQLException {
    checkClosed();
    return 0;
  }

  @Override public int getType() throws SQLException {
    checkClosed();
    return ResultSet.TYPE_FORWARD_ONLY;
  }

  @Override public int getConcurrency() throws SQLException {
    checkClosed();
    return ResultSet.CONCUR_READ_ONLY;
  }

  @Override public boolean rowUpdated() throws SQLException {
    checkClosed();
    return false;
  }

  @Override public boolean rowInserted() throws SQLException {
    checkClosed();
    return false;
  }

  @Override public boolean rowDeleted() throws SQLException {
    checkClosed();
    return false;
  }

  private void noUpdateImpl() throws SQLException {
    checkClosed();
    throw notSupported("Updates");
  }

  @Override public void updateNull(int columnIndex) throws SQLException { noUpdateImpl(); }
  @Override public void updateBoolean(int columnIndex, boolean x) throws SQLException { noUpdateImpl(); }
  @Override public void updateByte(int columnIndex, byte x) throws SQLException { noUpdateImpl(); }
  @Override public void updateShort(int columnIndex, short x) throws SQLException { noUpdateImpl(); }
  @Override public void updateInt(int columnIndex, int x) throws SQLException { noUpdateImpl(); }
  @Override public void updateLong(int columnIndex, long x) throws SQLException { noUpdateImpl(); }
  @Override public void updateFloat(int columnIndex, float x) throws SQLException { noUpdateImpl(); }
  @Override public void updateDouble(int columnIndex, double x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException { noUpdateImpl(); }
  @Override public void updateString(int columnIndex, String x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBytes(int columnIndex, byte[] x) throws SQLException { noUpdateImpl(); }
  @Override public void updateDate(int columnIndex, Date x) throws SQLException { noUpdateImpl(); }
  @Override public void updateTime(int columnIndex, Time x) throws SQLException { noUpdateImpl(); }
  @Override public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException { noUpdateImpl(); }
  @Override public void updateObject(int columnIndex, Object x) throws SQLException { noUpdateImpl(); }
  @Override public void updateNull(String columnLabel) throws SQLException { noUpdateImpl(); }
  @Override public void updateBoolean(String columnLabel, boolean x) throws SQLException { noUpdateImpl(); }
  @Override public void updateByte(String columnLabel, byte x) throws SQLException { noUpdateImpl(); }
  @Override public void updateShort(String columnLabel, short x) throws SQLException { noUpdateImpl();}
  @Override public void updateInt(String columnLabel, int x) throws SQLException { noUpdateImpl(); }
  @Override public void updateLong(String columnLabel, long x) throws SQLException { noUpdateImpl(); }
  @Override public void updateFloat(String columnLabel, float x) throws SQLException { noUpdateImpl(); }
  @Override public void updateDouble(String columnLabel, double x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException { noUpdateImpl(); }
  @Override public void updateString(String columnLabel, String x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBytes(String columnLabel, byte[] x) throws SQLException { noUpdateImpl(); }
  @Override public void updateDate(String columnLabel, Date x) throws SQLException { noUpdateImpl(); }
  @Override public void updateTime(String columnLabel, Time x) throws SQLException { noUpdateImpl(); }
  @Override public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException { noUpdateImpl(); }
  @Override public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException { noUpdateImpl(); }
  @Override public void updateObject(String columnLabel, Object x) throws SQLException { noUpdateImpl(); }

  @Override public void insertRow() throws SQLException { noUpdateImpl(); }
  @Override public void updateRow() throws SQLException { noUpdateImpl(); }
  @Override public void deleteRow() throws SQLException { noUpdateImpl(); }
  @Override public void refreshRow() throws SQLException { noUpdateImpl(); }
  @Override public void cancelRowUpdates() throws SQLException { noUpdateImpl(); }
  @Override public void moveToInsertRow() throws SQLException { noUpdateImpl(); }
  @Override public void moveToCurrentRow() throws SQLException { noUpdateImpl();}

  @Override public Statement getStatement() throws SQLException {
    checkClosed();
    return statement;
  }

  @Override public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
    var typeName = meta.getColumnTypeName(columnIndex);
    if (map != null && map.containsKey(typeName)) {
      throw notSupported(format("Custom type mapping for %s in SQLite", typeName));
    }
    return getObject(columnIndex);
  }

  @Override public Ref getRef(int columnIndex) throws SQLException {
    throw notSupported("REF type in SQLite");
  }

  @Override public Blob getBlob(int columnIndex) throws SQLException {
    var bytes = (byte[]) tryCast(columnIndex, Types.BLOB);
    if (bytes == null) {
      return null;
    }
    var blob = new L4Blob();
    blob.setBytes(1, bytes);
    return blob;
  }

  @Override public Clob getClob(int columnIndex) throws SQLException {
    return (Clob) tryCast(columnIndex, CLOB_STREAM);
  }

  @Override public Array getArray(int columnIndex) throws SQLException {
    throw notSupported("ARRAY type in SQLite");
  }

  @Override public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
    return getObject(findColumn(columnLabel), map);
  }

  @Override public Ref getRef(String columnLabel) throws SQLException {
    return getRef(findColumn(columnLabel));
  }

  @Override public Blob getBlob(String columnLabel) throws SQLException {
    return getBlob(findColumn(columnLabel));
  }

  @Override public Clob getClob(String columnLabel) throws SQLException {
    return getClob(findColumn(columnLabel));
  }

  @Override public Array getArray(String columnLabel) throws SQLException {
    return getArray(findColumn(columnLabel));
  }

  @Override public Date getDate(int columnIndex, Calendar cal) throws SQLException {
    return (Date) tryCast(columnIndex, Types.DATE, 0, cal);
  }

  @Override public Date getDate(String columnLabel, Calendar cal) throws SQLException {
    return getDate(findColumn(columnLabel), cal);
  }

  @Override public Time getTime(int columnIndex, Calendar cal) throws SQLException {
    return (Time) tryCast(columnIndex, Types.TIME, 0, cal);
  }

  @Override public Time getTime(String columnLabel, Calendar cal) throws SQLException {
    return getTime(findColumn(columnLabel), cal);
  }

  @Override public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
    return (Timestamp) tryCast(columnIndex, Types.TIMESTAMP, 0, cal);
  }

  @Override public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
    return getTimestamp(findColumn(columnLabel), cal);
  }

  @Override public URL getURL(int columnIndex) throws SQLException {
    return (URL) tryCast(columnIndex, URL_STREAM);
  }

  @Override public URL getURL(String columnLabel) throws SQLException {
    return getURL(findColumn(columnLabel));
  }

  @Override public void updateRef(int columnIndex, Ref x) throws SQLException { noUpdateImpl(); }
  @Override public void updateRef(String columnLabel, Ref x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(int columnIndex, Blob x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(String columnLabel, Blob x) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(int columnIndex, Clob x) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(String columnLabel, Clob x) throws SQLException { noUpdateImpl(); }
  @Override public void updateArray(int columnIndex, Array x) throws SQLException { noUpdateImpl(); }
  @Override public void updateArray(String columnLabel, Array x) throws SQLException { noUpdateImpl(); }

  @Override public RowId getRowId(int columnIndex) throws SQLException {
    checkClosed();
    checkRow(currentRow, result, isClosed);
    checkColumn(columnIndex, result);
    throw notSupported("RowId");
  }

  @Override public RowId getRowId(String columnLabel) throws SQLException {
    checkClosed();
    checkRow(currentRow, result, isClosed);
    findColumn(columnLabel);
    throw notSupported("RowId");
  }

  @Override public void updateRowId(int columnIndex, RowId x) throws SQLException { noUpdateImpl(); }
  @Override public void updateRowId(String columnLabel, RowId x) throws SQLException { noUpdateImpl(); }

  @Override public int getHoldability() throws SQLException {
    checkClosed();
    return ResultSet.CLOSE_CURSORS_AT_COMMIT;
  }

  @Override public boolean isClosed() {
    return isClosed;
  }

  @Override public void updateNString(int columnIndex, String nString) throws SQLException { noUpdateImpl(); }
  @Override public void updateNString(String columnLabel, String nString) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(int columnIndex, NClob nClob) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(String columnLabel, NClob nClob) throws SQLException { noUpdateImpl(); }

  @Override public NClob getNClob(int columnIndex) throws SQLException {
    return (NClob) tryCast(columnIndex, NCLOB_STREAM);
  }

  @Override public NClob getNClob(String columnLabel) throws SQLException {
    return getNClob(findColumn(columnLabel));
  }

  @Override public SQLXML getSQLXML(int columnIndex) throws SQLException {
    throw notSupported("SQLXML in SQLite");
  }

  @Override public SQLXML getSQLXML(String columnLabel) throws SQLException {
    return getSQLXML(findColumn(columnLabel));
  }

  @Override public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException { noUpdateImpl(); }
  @Override public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException { noUpdateImpl(); }

  @Override public String getNString(int columnIndex) throws SQLException {
    return (String) tryCast(columnIndex, Types.VARCHAR);
  }

  @Override public String getNString(String columnLabel) throws SQLException {
    return getNString(findColumn(columnLabel));
  }

  @Override public Reader getNCharacterStream(int columnIndex) throws SQLException {
    return (Reader) tryCast(columnIndex, NCHARACTER_STREAM);
  }

  @Override public Reader getNCharacterStream(String columnLabel) throws SQLException {
    return getNCharacterStream(findColumn(columnLabel));
  }

  @Override public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(int columnIndex, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(String columnLabel, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException { noUpdateImpl(); }
  @Override public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException { noUpdateImpl(); }
  @Override public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(int columnIndex, Reader x) throws SQLException { noUpdateImpl(); }
  @Override public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException { noUpdateImpl(); }
  @Override public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException { noUpdateImpl(); }
  @Override public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException { noUpdateImpl(); }
  @Override public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(int columnIndex, Reader reader) throws SQLException { noUpdateImpl(); }
  @Override public void updateClob(String columnLabel, Reader reader) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(int columnIndex, Reader reader) throws SQLException { noUpdateImpl(); }
  @Override public void updateNClob(String columnLabel, Reader reader) throws SQLException { noUpdateImpl(); }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
    return (T) tryCast(columnIndex, type);
  }

  @Override public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
    return getObject(findColumn(columnLabel), type);
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface == ResultSet.class || iface == Wrapper.class) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface == ResultSet.class || iface == Wrapper.class;
  }

}
