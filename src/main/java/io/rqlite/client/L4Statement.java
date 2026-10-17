package io.rqlite.client;

import io.rqlite.json.Json;
import io.rqlite.json.JsonArray;
import io.rqlite.json.JsonObject;
import io.rqlite.json.JsonValue;

import java.util.*;

import static io.rqlite.client.L4Err.*;

public class L4Statement {

  public String sql;
  public final List<Object> positionalParams = new ArrayList<>();
  public final Map<String, Object> namedParams = new LinkedHashMap<>();

  public L4Statement sql(String sql) {
    this.sql = Objects.requireNonNull(sql);
    return this;
  }

  private void badParams() {
    throw mixedStatementParams();
  }

  private void checkPositionalParams() {
    if (!positionalParams.isEmpty()) {
      badParams();
    }
  }

  private void checkNamedParams() {
    if (!namedParams.isEmpty()) {
      badParams();
    }
  }

  public L4Statement withPositionalParam(int paramIndex, Object param) {
    checkNamedParams();
    if (paramIndex < 0) {
      throw negativeParamIndex(paramIndex);
    }
    while (positionalParams.size() <= paramIndex) {
      positionalParams.add(null);
    }
    positionalParams.set(paramIndex, param);
    return this;
  }

  public L4Statement withPositionalParam(Object param) {
    checkNamedParams();
    positionalParams.add(param);
    return this;
  }

  public L4Statement withPositionalParams(Object... params) {
    checkNamedParams();
    this.positionalParams.clear();
    this.positionalParams.addAll(Arrays.asList(params));
    return this;
  }

  public L4Statement withNamedParam(String name, Object value) {
    checkPositionalParams();
    this.namedParams.put(name, value);
    return this;
  }

  public L4Statement withNamedParams(Map<String, Object> params) {
    checkPositionalParams();
    this.namedParams.clear();
    this.namedParams.putAll(params);
    return this;
  }

  public JsonArray build() {
    if (sql == null || sql.trim().isEmpty()) {
      throw emptyStatement();
    }
    var out = new JsonArray();
    out.add(sql);
    if (!namedParams.isEmpty()) {
      var paramsObject = new JsonObject();
      for (var entry : namedParams.entrySet()) {
        paramsObject.add(entry.getKey(), L4Json.toJsonValue(entry.getValue()));
      }
      out.add(paramsObject);
    } else if (!positionalParams.isEmpty()) {
      for (var param : positionalParams) {
        out.add(L4Json.toJsonValue(param));
      }
    }
    return out;
  }

  public static JsonValue toArray(L4Statement... statements) {
    var smtList = Json.array();
    for (var smt : statements) {
      smtList.add(smt.build());
    }
    return smtList;
  }

  @Override public String toString() {
    return String.format("[%s]", sql);
  }

}
