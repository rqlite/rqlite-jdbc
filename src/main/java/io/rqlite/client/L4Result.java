package io.rqlite.client;

import io.rqlite.json.JsonObject;
import java.io.PrintStream;
import java.util.*;
import java.util.function.BiConsumer;

import static io.rqlite.client.L4Json.*;

public class L4Result {

  public List<String> columns;
  public List<String> types;
  public List<List<String>> values;
  public Long lastInsertId;
  public Integer rowsAffected;
  public String error;

  public L4Result(JsonObject json) {
    if (json.get("error") != null) {
      this.error = json.getString("error", "Unknown error");
    } else {
      this.columns = json.get("columns") != null ? toStringList(json.get("columns").asArray()) : new ArrayList<>();
      this.types = json.get("types") != null ? toStringList(json.get("types").asArray()) : new ArrayList<>();
      this.values = json.get("values") != null ? toValuesList(json.get("values").asArray()) : new ArrayList<>();
      this.lastInsertId = json.get("last_insert_id") != null ? json.getLong("last_insert_id", 0) : 0;
      this.rowsAffected = json.get("rows_affected") != null ? json.getInt("rows_affected",0) : 0;
    }
  }

  public int indexOf(String column) {
    for (int i = 0; i < columns.size(); i++) {
      var name = columns.get(i);
      if (name.equalsIgnoreCase(column)) {
        return i;
      }
    }
    return -1;
  }

  public void forEach(BiConsumer<Integer, List<String>> rowFn) {
    if (values != null) {
      for (int i = 0; i < values.size(); i++) {
        rowFn.accept(i, values.get(i));
      }
    }
  }

  public L4Result addRow(String ... values) {
    var row = Arrays.asList(values);
    this.values.add(row);
    return this;
  }

  public L4Result setTypes(String ... types) {
    this.types = Arrays.asList(types);
    return this;
  }

  public String get(String col, List<String> row) {
    return row.get(indexOf(col));
  }

  public void set(String col, String val, List<String> row) {
    row.set(indexOf(col), val);
  }

  public void print(PrintStream out) {
    Objects.requireNonNull(out, "PrintStream cannot be null");

    if (error != null) {
      out.printf("Error: %s%n", error);
      return;
    }
    if (columns == null || columns.isEmpty()) {
      out.println("No columns available.");
      return;
    }

    var maxWidths = new int[columns.size()]; // Calculate max width for each column
    for (int i = 0; i < columns.size(); i++) {
      maxWidths[i] = columns.get(i).length();
      for (List<String> row : values) {
        String value = row.get(i);
        maxWidths[i] = Math.max(maxWidths[i], value != null ? value.length() : 4); // 4 for "null"
      }
    }

    out.println();

    for (int i = 0; i < columns.size(); i++) { // Print column headers
      out.printf("| %-" + maxWidths[i] + "s ", columns.get(i));
    }
    out.println("|");

    for (int width : maxWidths) { // Print separator
      out.print("| ");
      out.print("-".repeat(width));
      out.print(" ");
    }
    out.println("|");

    for (List<String> row : values) { // Print rows
      for (int i = 0; i < row.size(); i++) {
        String value = row.get(i) != null ? row.get(i) : "null";
        out.printf("| %-" + maxWidths[i] + "s ", value);
      }
      out.println("|");
    }

    if (rowsAffected != null) { // Print summary
      out.printf("%nRows affected: %d%n", rowsAffected);
    }
    if (lastInsertId != null) {
      out.printf("Last insert ID: %d%n", lastInsertId);
    }
  }

  @Override public String toString() {
    return String.format(
      "[cols: %d, types: %d, vals: %d, id: %d, rows: %d, err: %s]",
      columns != null ? columns.size() : -1,
      types != null ? types.size() : -1,
      values != null ? values.size() : -1,
      lastInsertId, rowsAffected, error
    );
  }

}