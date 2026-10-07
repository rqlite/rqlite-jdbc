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

  private static final char[] HexDigits = "0123456789abcdef".toCharArray();

  /**
   * Encodes a byte array as a SQLite {@code x'hex'} BLOB literal. rqlite stores this as a
   * real BLOB (base64 strings are stored as TEXT), and it is more compact than a JSON array
   * of byte integers.
   */
  public static String toHexLiteral(byte[] bytes) {
    var sb = new StringBuilder(3 + bytes.length * 2);
    sb.append("x'");
    for (byte b : bytes) {
      sb.append(HexDigits[(b >> 4) & 0xF]);
      sb.append(HexDigits[b & 0xF]);
    }
    sb.append('\'');
    return sb.toString();
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
      return Json.value(toHexLiteral((byte[]) value));
    } else {
      return Json.value(value.toString());
    }
  }

}
