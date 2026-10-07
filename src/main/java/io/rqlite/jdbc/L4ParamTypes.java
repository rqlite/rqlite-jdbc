package io.rqlite.jdbc;

import io.rqlite.client.L4Client;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Best-effort inference of JDBC types for prepared-statement parameters.
 *
 * <p>SQLite is dynamically typed and exposes no parameter-type API, so a parameter's type
 * cannot be known authoritatively. This maps each positional placeholder to its target
 * column (for common {@code INSERT}/{@code UPDATE}/{@code SELECT} shapes) and reports the
 * column's declared type. When a placeholder cannot be mapped, its type is unknown.
 */
public class L4ParamTypes {

  private static final Pattern INSERT = Pattern.compile(
    "(?is)\\binsert\\s+(?:or\\s+\\w+\\s+)?into\\s+([`\"\\[\\]\\w.]+)\\s*\\(([^)]*)\\)\\s*values\\s*\\(([^)]*)\\)");
  private static final Pattern UPDATE = Pattern.compile(
    "(?is)\\bupdate\\s+([`\"\\[\\]\\w.]+)");
  private static final Pattern FROM = Pattern.compile(
    "(?is)\\bfrom\\s+([`\"\\[\\]\\w.]+)");
  private static final Pattern COL_PREDICATE = Pattern.compile(
    "(?is)\\b([`\"\\[\\]\\w]+)\\s*(?:=|<>|!=|<=|>=|<|>|\\blike\\b|\\bis\\b)\\s*\\?");
  private static final Pattern COL_IN = Pattern.compile(
    "(?is)\\b([`\"\\[\\]\\w]+)\\s+in\\s*\\(\\s*\\?");

  /**
   * Returns a map of positional parameter index to declared rqlite/SQLite type. May be
   * partial or empty when the statement shape is not recognised.
   */
  public static Map<Integer, String> infer(L4Client client, String sql, List<L4Jdbc.Placeholder> placeholders) {
    var result = new HashMap<Integer, String>();
    if (client == null || sql == null || placeholders.isEmpty()) {
      return result;
    }
    var table = targetTable(sql);
    if (table == null) {
      return result;
    }
    Map<String, String> columns;
    try {
      columns = tableColumns(client, table);
    } catch (Exception e) {
      return result;
    }
    if (columns.isEmpty()) {
      return result;
    }

    var insert = INSERT.matcher(sql);
    if (insert.find()) {
      var cols = splitIdentifiers(insert.group(2));
      int valuesStart = insert.start(3);
      int valuesEnd = insert.end(3);
      int colIdx = 0;
      for (var p : placeholders) {
        if (p.isNamed() || p.offset < valuesStart || p.offset >= valuesEnd) {
          continue;
        }
        if (colIdx < cols.size()) {
          var type = columns.get(cols.get(colIdx).toLowerCase());
          if (type != null) {
            result.put(p.index, type);
          }
        }
        colIdx++;
      }
    }

    mapPredicate(sql, placeholders, columns, result, COL_PREDICATE);
    mapPredicate(sql, placeholders, columns, result, COL_IN);

    return result;
  }

  private static void mapPredicate(String sql, List<L4Jdbc.Placeholder> placeholders,
                                   Map<String, String> columns, Map<Integer, String> result, Pattern pattern) {
    var m = pattern.matcher(sql);
    while (m.find()) {
      var type = columns.get(normalizeIdent(m.group(1)).toLowerCase());
      if (type == null) {
        continue;
      }
      int offset = m.end() - 1; // the pattern ends with the '?' placeholder
      for (var p : placeholders) {
        if (!p.isNamed() && p.offset == offset) {
          result.put(p.index, type);
          break;
        }
      }
    }
  }

  private static String targetTable(String sql) {
    var s = sql.trim();
    var m = INSERT.matcher(s);
    if (m.find()) {
      return normalizeIdent(m.group(1));
    }
    m = UPDATE.matcher(s);
    if (m.find()) {
      return normalizeIdent(m.group(1));
    }
    m = FROM.matcher(s);
    if (m.find()) {
      return normalizeIdent(m.group(1));
    }
    return null;
  }

  private static Map<String, String> tableColumns(L4Client client, String table) {
    var res = client.querySingle("PRAGMA table_info(\"" + table.replace("\"", "\"\"") + "\")").first();
    var out = new HashMap<String, String>();
    res.forEach((i, row) -> {
      var name = res.get("name", row);
      var type = res.get("type", row);
      if (name != null) {
        out.put(name.toLowerCase(), type == null ? "" : type);
      }
    });
    return out;
  }

  private static List<String> splitIdentifiers(String list) {
    var out = new ArrayList<String>();
    for (var part : list.split(",")) {
      var t = part.trim();
      if (!t.isEmpty()) {
        out.add(normalizeIdent(t));
      }
    }
    return out;
  }

  private static String normalizeIdent(String ident) {
    var s = ident.trim();
    int dot = s.lastIndexOf('.');
    if (dot >= 0) {
      s = s.substring(dot + 1);
    }
    if (s.length() >= 2) {
      char a = s.charAt(0);
      char b = s.charAt(s.length() - 1);
      if ((a == '"' && b == '"') || (a == '`' && b == '`') || (a == '[' && b == ']')) {
        s = s.substring(1, s.length() - 1);
      }
    }
    return s.replace("\"\"", "\"").replace("``", "`");
  }

}
