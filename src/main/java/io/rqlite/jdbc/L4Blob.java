package io.rqlite.jdbc;

import javax.sql.rowset.serial.SerialBlob;
import java.io.*;
import java.sql.*;
import java.util.Arrays;

import static io.rqlite.jdbc.L4Err.*;

public class L4Blob implements Blob {

  private ByteArrayOutputStream data;
  private SerialBlob serialBlob;
  private boolean isClosed = false;

  public L4Blob() throws SQLException {
    this.data = new ByteArrayOutputStream();
    this.serialBlob = new SerialBlob(new byte[0]);
  }

  private void checkClosed() throws SQLException {
    if (isClosed) {
      throw new SQLException("Blob is closed", L4Err.SqlStateGeneralError);
    }
  }

  private void updateSerialBlob() throws SQLException {
    try {
      this.serialBlob = new SerialBlob(data.toByteArray());
    } catch (Exception e) {
      throw badUpdate(e);
    }
  }

  @Override public long length() throws SQLException {
    checkClosed();
    return serialBlob.length();
  }

  @Override public byte[] getBytes(long pos, int length) throws SQLException {
    checkClosed();
    return serialBlob.getBytes(pos, length);
  }

  @Override public InputStream getBinaryStream() throws SQLException {
    checkClosed();
    return serialBlob.getBinaryStream();
  }

  @Override public InputStream getBinaryStream(long pos, long length) throws SQLException {
    checkClosed();
    return serialBlob.getBinaryStream(pos, length);
  }

  @Override public long position(byte[] pattern, long start) throws SQLException {
    checkClosed();
    return serialBlob.position(pattern, start);
  }

  @Override public long position(Blob pattern, long start) throws SQLException {
    checkClosed();
    return serialBlob.position(pattern, start);
  }

  @Override public int setBytes(long pos, byte[] bytes) throws SQLException {
    checkClosed();
    try {
      var current = data.toByteArray();
      data.reset();
      int start = (int) pos - 1;
      data.write(current, 0, start);
      data.write(bytes);
      if (start + bytes.length < current.length) {
        data.write(current, start + bytes.length, current.length);
      }
      updateSerialBlob();
      return bytes.length;
    } catch (IOException e) {
      throw badUpdate(e);
    }
  }

  @Override public int setBytes(long pos, byte[] bytes, int offset, int len) throws SQLException {
    checkClosed();
    var subBytes = Arrays.copyOfRange(bytes, offset, offset + len);
    return setBytes(pos, subBytes);
  }

  @Override public OutputStream setBinaryStream(long pos) throws SQLException {
    checkClosed();
    return new ByteArrayOutputStream() {
      @Override
      public void write(byte[] b) throws IOException {
        try {
          setBytes(pos, b);
        } catch (SQLException e) {
          throw new IOException(e);
        }
      }
    };
  }

  @Override public void truncate(long len) throws SQLException {
    checkClosed();
    byte[] bytes = data.toByteArray();
    data.reset();
    data.write(bytes, 0, (int) len);
    updateSerialBlob();
  }

  @Override public void free() throws SQLException {
    if (!isClosed) {
      data = null;
      serialBlob.free();
      serialBlob = null;
      isClosed = true;
    }
  }

}
