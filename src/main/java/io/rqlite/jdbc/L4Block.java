package io.rqlite.jdbc;

import java.sql.SQLException;

public interface L4Block<T> {

  T get() throws Exception;

  static <T> T sqlRun(L4Block<T> block) throws SQLException {
    try {
      return block.get();
    } catch (Exception e) {
      throw new SQLException(e.getMessage(), L4Err.SqlStateGeneralError, e);
    }
  }

}
