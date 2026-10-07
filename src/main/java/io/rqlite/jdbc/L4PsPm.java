package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Statement;
import java.sql.*;
import java.util.*;

import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Err.*;

public class L4PsPm implements ParameterMetaData {

  private final L4Client client;
  private final L4Statement statement;
  private final List<Placeholder> placeholders;
  private Map<Integer, String> inferredTypes;

  public L4PsPm(L4Client client, L4Statement statement) {
    this.client = client;
    this.statement = Objects.requireNonNull(statement);
    this.placeholders = scanPlaceholders(statement.sql);
  }

  private Map<Integer, String> inferredTypes() {
    if (inferredTypes == null) {
      try {
        inferredTypes = L4ParamTypes.infer(client, statement.sql, placeholders);
      } catch (Exception e) {
        inferredTypes = new HashMap<>();
      }
    }
    return inferredTypes;
  }

  private String declaredType(int param) {
    return inferredTypes().get(param);
  }

  @Override public int getParameterCount() {
    var positional = positionalParameterCount(placeholders);
    if (positional > 0) {
      return positional;
    }
    return namedParameterNames(placeholders).size();
  }

  @Override public int isNullable(int param) {
    return ParameterMetaData.parameterNullableUnknown;
  }

  @Override public boolean isSigned(int param) {
    var type = declaredType(param);
    return type != null && getJdbcTypeSigned(type);
  }

  @Override public int getPrecision(int param) {
    var type = declaredType(param);
    return type == null ? 0 : getJdbcTypePrecision(type);
  }

  @Override public int getScale(int param) {
    return 0;
  }

  @Override public int getParameterType(int param) {
    var type = declaredType(param);
    if (type == null || type.isEmpty()) {
      return Types.OTHER;
    }
    var jt = getJdbcType(type);
    return jt == -1 ? Types.OTHER : jt;
  }

  @Override public String getParameterTypeName(int param) {
    return declaredType(param);
  }

  @Override public String getParameterClassName(int param) {
    var type = declaredType(param);
    if (type == null || type.isEmpty()) {
      return null;
    }
    return getJdbcTypeClassName(type);
  }

  @Override public int getParameterMode(int param) {
    return ParameterMetaData.parameterModeIn;
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    return L4Err.unwrap(iface, this);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return L4Err.isWrapperFor(iface, this);
  }

}
