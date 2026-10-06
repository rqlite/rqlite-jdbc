package io.rqlite.client;

import io.rqlite.json.Json;
import io.rqlite.json.JsonArray;
import io.rqlite.json.JsonValue;

import java.util.*;

public class L4Json {

  public static List<String> toStringList(JsonArray array) {
    var list = new ArrayList<String>();
    for (var value : array) {
      list.add(value.asString());
    }
    return list;
  }

  public static List<List<String>> toValuesList(JsonArray valuesArray) {
    var values = new ArrayList<List<String>>();
    for (var rowValue : valuesArray) {
      var row = rowValue.asArray();
      var rowValues = new ArrayList<String>();
      for (int i = 0; i < row.size(); i++) {
        var cell = row.get(i);
        if (cell.isNull()) {
          rowValues.add(null);
        } else if (cell.isString()) {
          rowValues.add(cell.asString());
        } else {
          rowValues.add(cell.toString());
        }
      }
      values.add(rowValues);
    }
    return values;
  }

  public static JsonValue toJsonValue(Object value) {
    if (value == null) {
      return Json.NULL;
    } else if (value instanceof String) {
      return Json.value((String) value);
    } else if (value instanceof Integer) {
      return Json.value((Integer) value);
    } else if (value instanceof Long) {
      return Json.value((Long) value);
    } else if (value instanceof Double) {
      return Json.value((Double) value);
    } else if (value instanceof Float) {
      return Json.value((Float) value);
    } else if (value instanceof Boolean) {
      return Json.value((Boolean) value);
    } else if (value instanceof byte[]) {
      // rqlite expects BLOB parameter values as an array of unsigned byte integers
      // (or an x'hex' string). Base64 strings are stored as TEXT, not BLOB.
      var bytes = Json.array();
      for (byte b : (byte[]) value) {
        bytes.add(b & 0xFF);
      }
      return bytes;
    } else {
      return Json.value(value.toString());
    }
  }

}
