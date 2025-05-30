package io.rqlite.jdbc;

import javax.sql.rowset.serial.SerialClob;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

import static io.rqlite.jdbc.L4Err.*;

public class L4Clob implements Clob {

  private StringBuilder data;
  private SerialClob serialClob;
  private boolean isClosed = false;

  public L4Clob() throws SQLException {
    this.data = new StringBuilder();
    this.serialClob = new SerialClob(new char[0]);
  }

  private void checkClosed() throws SQLException {
    if (isClosed) {
      throw generalError("Clob is closed");
    }
  }

  private void updateSerialClob() throws SQLException {
    try {
      this.serialClob = new SerialClob(data.toString().toCharArray());
    } catch (Exception e) {
      throw badUpdate(e);
    }
  }

  @Override public long length() throws SQLException {
    checkClosed();
    return serialClob.length();
  }

  @Override public String getSubString(long pos, int length) throws SQLException {
    checkClosed();
    return serialClob.getSubString(pos, length);
  }

  @Override public Reader getCharacterStream() throws SQLException {
    checkClosed();
    return serialClob.getCharacterStream();
  }

  @Override public Reader getCharacterStream(long pos, long length) throws SQLException {
    checkClosed();
    return serialClob.getCharacterStream(pos, length);
  }

  @Override public InputStream getAsciiStream() throws SQLException {
    checkClosed();
    return serialClob.getAsciiStream();
  }

  @Override public long position(String searchstr, long start) throws SQLException {
    checkClosed();
    return serialClob.position(searchstr, start);
  }

  @Override public long position(Clob searchstr, long start) throws SQLException {
    checkClosed();
    return serialClob.position(searchstr, start);
  }

  @Override public int setString(long pos, String str) throws SQLException {
    checkClosed();
    int start = (int) pos - 1;
    if (start + str.length() > data.length()) {
      data.setLength(start);
      data.append(str);
    } else {
      data.replace(start, start + str.length(), str);
    }
    updateSerialClob();
    return str.length();
  }

  @Override public int setString(long pos, String str, int offset, int len) throws SQLException {
    checkClosed();
    var substr = str.substring(offset, offset + len);
    return setString(pos, substr);
  }

  @Override public Writer setCharacterStream(long pos) throws SQLException {
    checkClosed();
    return new StringWriter() {
      @Override public void write(String str) {
        try {
          setString(pos, str);
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }

  @Override public OutputStream setAsciiStream(long pos) throws SQLException {
    checkClosed();
    return new ByteArrayOutputStream() {
      @Override public void write(byte[] b) throws IOException {
        try {
          setString(pos, new String(b, StandardCharsets.US_ASCII));
        } catch (SQLException e) {
          throw new IOException(e);
        }
      }
    };
  }

  @Override public void truncate(long len) throws SQLException {
    checkClosed();
    data.setLength((int) len);
    updateSerialClob();
  }

  @Override public void free() throws SQLException {
    if (!isClosed) {
      data = null;
      serialClob.free();
      serialClob = null;
      isClosed = true;
    }
  }

}
