package io.rqlite.client;

import io.rqlite.json.JsonObject;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class L4Response {

  public final List<L4Result> results;
  public final Float time;
  public final int statusCode;

  public L4Response(int statusCode, JsonObject obj) {
    this.statusCode = statusCode;
    var res = obj.get("results");
    if (res != null) {
      var resultsArray = res.asArray();
      this.results = new ArrayList<>();
      for (var resultValue : resultsArray) {
        this.results.add(new L4Result(resultValue.asObject()));
      }
    } else {
      this.results = new ArrayList<>();
    }
    this.time = obj.get("time") != null ? obj.getFloat("time", -1) : null;
  }

  public void print(PrintStream out) {
    for (var res : results) {
      res.print(out);
    }
  }

  public L4Result first() {
    if (results != null && !results.isEmpty()) {
      return results.get(0);
    }
    return null;
  }

}