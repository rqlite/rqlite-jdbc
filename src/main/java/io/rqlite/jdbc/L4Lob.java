package io.rqlite.jdbc;

import java.sql.SQLException;

/**
 * Shared lifecycle for the driver's {@code Blob}/{@code Clob} implementations.
 */
abstract class L4Lob {

  protected boolean isClosed = false;

  protected void checkClosed(String kind) throws SQLException {
    if (isClosed) {
      throw L4Err.generalError(kind + " is closed");
    }
  }

  /** Releases the underlying resources. Called once from {@link #free()}. */
  protected abstract void release() throws SQLException;

  public void free() throws SQLException {
    if (!isClosed) {
      release();
      isClosed = true;
    }
  }

}
