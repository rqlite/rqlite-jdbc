package io.rqlite.jdbc;

import io.rqlite.client.L4Statement;
import java.sql.*;
import java.util.Objects;

import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Err.*;

public class L4PsPm implements ParameterMetaData {

  private final L4Statement statement;

  public L4PsPm(L4Statement statement) {
    this.statement = Objects.requireNonNull(statement);
  }

  private Object paramAt(int paramIdx) {
    paramIdx = paramIdx - 1;
    if (paramIdx >= statement.positionalParams.size()) {
      return null;
    }
    return statement.positionalParams.get(paramIdx);
  }

  @Override public int getParameterCount() {
    return statement.positionalParams.size();
  }

  @Override public int isNullable(int param) {
    return ParameterMetaData.parameterNullableUnknown;
  }

  @Override public boolean isSigned(int param) {
    var rqt = rqTypeOf(paramAt(param));
    return getJdbcTypeSigned(rqt);
  }

  @Override public int getPrecision(int param) {
    var rqt = rqTypeOf(paramAt(param));
    return getJdbcTypePrecision(rqt);
  }

  @Override public int getScale(int param) {
    return 0; // TODO I don't think this is correct.
  }

  @Override public int getParameterType(int param) {
    var rqt = rqTypeOf(paramAt(param));
    return getJdbcType(rqt);
  }

  @Override public String getParameterTypeName(int param) {
    return rqTypeOf(paramAt(param));
  }

  @Override public String getParameterClassName(int param) {
    var o = paramAt(param);
    return o == null ? null : o.getClass().getCanonicalName();
  }

  @Override public int getParameterMode(int param) {
    return ParameterMetaData.parameterModeIn;
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface == ParameterMetaData.class || iface == Wrapper.class) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface == ParameterMetaData.class || iface == Wrapper.class;
  }

}
