package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Result;

import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.*;
import java.util.regex.Pattern;

import static io.rqlite.client.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;
import static java.lang.String.*;

public class L4Db {

  public static final String Keywords = join(",",
    "ACTION,ADD,AFTER,ALL,ALTER,ANALYZE,AND,AS,ASC,ATTACH,AUTOINCREMENT,",
    "BEFORE,BEGIN,BETWEEN,BY,",
    "CASCADE,CASE,CAST,CHECK,COLLATE,COLUMN,COMMIT,CONFLICT,CONSTRAINT,CREATE,",
    "CROSS,CURRENT_DATE,CURRENT_TIME,CURRENT_TIMESTAMP,",
    "DATABASE,DEFAULT,DEFERRABLE,DEFERRED,DELETE,DESC,DETACH,DISTINCT,DROP,",
    "EACH,ELSE,END,ESCAPE,EXCEPT,EXCLUSIVE,EXISTS,EXPLAIN,",
    "FAIL,FOR,FOREIGN,FROM,FULL,GLOB,GROUP,HAVING,",
    "IF,IGNORE,IMMEDIATE,IN,INDEX,INDEXED,INITIALLY,INNER,INSERT,INSTEAD,INTERSECT,INTO,IS,ISNULL,",
    "JOIN,KEY,LEFT,LIKE,LIMIT,MATCH,NATURAL,NO,NOT,NOTNULL,NULL,OF,OFFSET,ON,OR,ORDER,OUTER,",
    "PLAN,PRAGMA,PRIMARY,QUERY,RAISE,RECURSIVE,REFERENCES,REGEXP,REINDEX,RELEASE,RENAME,REPLACE,",
    "RESTRICT,RIGHT,ROLLBACK,ROW,SAVEPOINT,SELECT,SET,TABLE,TEMP,TEMPORARY,THEN,TO,TRANSACTION,",
    "TRIGGER,UNION,UNIQUE,UPDATE,USING,VACUUM,VALUES,VIEW,VIRTUAL,WHEN,WHERE,WITH,WITHOUT"
  );

  public static final String FnNumeric  = "abs,coalesce,likelihood,likely,max,min,random,randomblob,round,sign,unlikely,zeroblob";
  public static final String FnString   = "char,concat,concat_ws,format,glob,hex,instr,length,like,lower,ltrim,octet_length,printf,replace,rtrim,soundex,substr,substring,trim,unicode,unhex,upper";
  public static final String FnSystem   = "changes,iif,ifnull,last_insert_rowid,nullif,quote,sqlite_compileoption_get,sqlite_compileoption_used,sqlite_offset,sqlite_source_id,sqlite_version,total_changes,typeof";
  public static final String FnDateTime = "date,datetime,julianday,strftime,time";

  public static final String
    BUFFER_LENGTH = "BUFFER_LENGTH",
    CASCADE = "CASCADE", COLUMN_NAME = "COLUMN_NAME", COLUMN_SIZE = "COLUMN_SIZE",
    DATA_TYPE = "DATA_TYPE", DECIMAL_DIGITS = "DECIMAL_DIGITS", DELETE_RULE = "DELETE_RULE",
    DEFERRABILITY = "DEFERRABILITY",
    FK_NAME = "FK_NAME",
    KEY_SEQ = "KEY_SEQ", NULLABLE = "NULLABLE", UPDATE_RULE = "UPDATE_RULE",

    TABLE = "TABLE", VIEW = "VIEW",
    TABLE_CAT = "TABLE_CAT", TABLE_NAME = "TABLE_NAME", TYPE_NAME = "TYPE_NAME",

    PK_NAME = "PK_NAME",
    PKTABLE_NAME = "PKTABLE_NAME",
    PKCOLUMN_NAME = "PKCOLUMN_NAME",

    FKTABLE_NAME = "FKTABLE_NAME",
    FKCOLUMN_NAME = "FKCOLUMN_NAME",

    kCid = "cid", kDesc = "desc", kFrom = "from",
    kName = "name", kOnDelete = "on_delete",
    kTable = "table", kTo = "to",
    kType = "type", kNotNull = "notnull",
    kDfltValue = "dflt_value", kPk = "pk", kSeq = "seq",
    kNull = "null", kUnique = "unique",

    YES = "YES", NO = "NO", Main = "main", All = "%"
  ;

  private static String itoa(int val) {
    return Integer.toString(val);
  }

  private static int atoi(String val) {
    return Integer.parseInt(val);
  }

  private static String btoa(boolean b) {
    return Boolean.toString(b);
  }

  private static boolean atob(String val) { return Boolean.parseBoolean(val); }

  private static boolean matchesPattern(String value, String pattern) {
    if (pattern == null || pattern.equals("%")) {
      return true;
    }
    if (value == null) {
      return false;
    }
    // Convert SQL LIKE pattern to regex (e.g., % -> .*, _ -> .)
    var regex = pattern.replace("%", ".*").replace("_", ".");
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).matches();
  }

  public static L4Result dbGetCatalogs(L4Client client) {
    var res = checkResult(client.querySingle("SELECT * from (SELECT NULL TABLE_CAT) WHERE 1 = 0").first());
    var out = res.setTypes(RQ_VARCHAR);
    out.addRow(Main);
    return out;
  }

  public static L4Result dbGetTables(String tableNamePattern, String[] types, L4Client client) {
    var typeSet = types == null ? new HashSet<>(Arrays.asList(TABLE, VIEW)) : new HashSet<>(Arrays.asList(types));
    var typeFilter = "";
    if (!typeSet.contains(TABLE) && typeSet.contains(VIEW)) {
      typeFilter = " WHERE type = 'view'";
    } else if (typeSet.contains(TABLE) && !typeSet.contains(VIEW)) {
      typeFilter = " WHERE type = 'table'";
    } else {
      typeFilter = "WHERE (type = 'table' OR type = 'view')";
    }
    var sql = join("\n", "",
      "SELECT ",
      "  NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, name AS TABLE_NAME, ",
      "  type AS TABLE_TYPE, NULL AS REMARKS, NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, ",
      "  NULL AS TYPE_NAME, NULL AS SELF_REFERENCING_COL_NAME, NULL AS REF_GENERATION",
      "FROM sqlite_master",
      "%s",
      "AND name LIKE '%s'"
    );
    tableNamePattern = tableNamePattern == null ? "%" : quote(tableNamePattern);
    sql = format(sql, typeFilter, tableNamePattern);
    var res = client.querySingle(sql).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR
    );
    res.forEach((i, row) -> res.set(TABLE_CAT, Main, row));
    return res;
  }

  public static List<String> dbUserTables(L4Client client) {
    var out = new ArrayList<String>();
    var trs = dbGetTables(All, new String[] { TABLE }, client);
    trs.forEach((i, row) -> {
      var table = trs.get(TABLE_NAME, row);
      if (!table.startsWith("sqlite")) {
        out.add(table);
      }
    });
    return out;
  }

  public static L4Result dbGetTableTypes(L4Client client) {
    return client
      .querySingle("SELECT * FROM (SELECT NULL TABLE_TYPE) WHERE 1 = 0")
      .first().setTypes(RQ_VARCHAR)
      .addRow(TABLE).addRow(VIEW);
  }

  public static L4Result dbGetColumns(String tableNamePattern, String columnNamePattern, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, ",
      "         NULL COLUMN_NAME, 0 AS DATA_TYPE, NULL TYPE_NAME, 0 AS COLUMN_SIZE, ",
      "         0 AS BUFFER_LENGTH, 0 AS DECIMAL_DIGITS, 0 AS NUM_PREC_RADIX, 0 AS NULLABLE, ",
      "         NULL REMARKS, NULL COLUMN_DEF, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, ",
      "         0 AS CHAR_OCTET_LENGTH, 0 AS ORDINAL_POSITION, NULL IS_NULLABLE, NULL SCOPE_CATALOG, ",
      "         NULL SCOPE_SCHEMA, NULL SCOPE_TABLE, 0 AS SOURCE_DATA_TYPE, NULL IS_AUTOINCREMENT, ",
      "         NULL IS_GENERATEDCOLUMN",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_INTEGER, RQ_VARCHAR, RQ_INTEGER, RQ_INTEGER,
      RQ_INTEGER, RQ_INTEGER, RQ_INTEGER, RQ_VARCHAR,
      RQ_VARCHAR, RQ_INTEGER, RQ_INTEGER, RQ_INTEGER,
      RQ_INTEGER, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_INTEGER, RQ_VARCHAR, RQ_VARCHAR
    );

    // Get all tables matching tableNamePattern
    var tables = dbGetTables(tableNamePattern, new String[] {TABLE, VIEW}, client);

    tables.forEach((i, row) -> {
      var tableName = out.get(TABLE_NAME, row);
      var res0 = client.querySingle(format("PRAGMA table_info('%s')", quote(tableName))).first();
      res0.forEach((j, row0) -> {
        var ordinal = j + 1;
        var colName = res0.get(kName, row0);
        if (matchesPattern(colName, columnNamePattern)) {
          var type = res0.get(kType, row0);
          var notNull = atoi(res0.get(kNotNull, row0));
          var defaultValue = res0.get(kDfltValue, row0);
          var pk = atoi(res0.get(kPk, row0));
          var isAutoIncrement = pk == 1 && type.contains(RQ_INTEGER) && defaultValue != null && defaultValue.equalsIgnoreCase(kNull);
          var sqlType = getJdbcType(type);
          var columnSize = getJdbcTypePrecision(type);
          var decimalDigits = 0;
          var nullable = notNull == 1 ? DatabaseMetaData.columnNoNulls : DatabaseMetaData.columnNullable;
          out.addRow(Main,
            null, tableName, colName, itoa(sqlType), type,
            itoa(columnSize), itoa(0), itoa(decimalDigits), itoa(10),
            itoa(nullable),
            null,                       // REMARKS
            defaultValue,               // COLUMN_DEF
            itoa(sqlType),              // SQL_DATA_TYPE
            itoa(0),                    // SQL_DATETIME_SUB
            itoa(columnSize),           // CHAR_OCTET_LENGTH
            itoa(ordinal),              // ORDINAL_POSITION
            notNull == 0 ? YES : NO,    // IS_NULLABLE
            null,                       // SCOPE_CATALOG
            null,                       // SCOPE_SCHEMA
            null,                       // SCOPE_TABLE
            itoa(0),                    // SOURCE_DATA_TYPE
            isAutoIncrement ? YES : NO, // IS_AUTOINCREMENT
            NO                          // IS_GENERATEDCOLUMN
          );
        }
      });
    });

    return out;
  }

  public static L4Result dbGetPrimaryKeys(String tablePattern, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, ",
      "         NULL AS TABLE_NAME, NULL AS COLUMN_NAME, 0 AS KEY_SEQ, NULL AS PK_NAME",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_INTEGER, RQ_VARCHAR
    );
    var tables = tablePattern.equals(All) ? dbUserTables(client) : List.of(tablePattern);
    for (var table : tables) {
      var tab = quote(table);
      var res = client.querySingle(format("PRAGMA table_info('%s')", tab)).first();
      final var keySeq = new int[] { 1 };
      res.forEach((i, row) -> {
        var pk = atoi(res.get(kPk, row));
        if (pk == 1) {
          out.addRow(
            Main, null, tab, res.get(kName, row),
            itoa(keySeq[0]++), // TODO double check this.
            format("PK_%s", tab)
          );
        }
      });
    }
    return out;
  }

  public static L4Result dbGetBestRowIdentifier(String table, boolean nullable, L4Client client) {
    // Use primary key columns as best row identifier
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT 0 AS SCOPE, NULL AS COLUMN_NAME, ",
      "         0 AS DATA_TYPE, NULL AS TYPE_NAME, 0 AS COLUMN_SIZE, ",
      "         0 AS BUFFER_LENGTH, 0 AS DECIMAL_DIGITS, 0 AS PSEUDO_COLUMN",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_INTEGER, RQ_VARCHAR, RQ_INTEGER,
      RQ_VARCHAR, RQ_INTEGER, RQ_INTEGER,
      RQ_INTEGER, RQ_INTEGER
    );
    var pkRs = dbGetPrimaryKeys(table, client);
    pkRs.forEach((i, pkr) -> {
      var colName = pkRs.get(COLUMN_NAME, pkr);
      var colRs = dbGetColumns(table, colName, client);
      colRs.forEach((j, cr) -> {
        var nFlag = colRs.get(NULLABLE, cr);
        var nfi = nFlag != null ? Integer.parseInt(nFlag) : -1;
        if (!nullable || nfi == DatabaseMetaData.columnNoNulls) {
          out.addRow(
            itoa(DatabaseMetaData.bestRowSession),
            colRs.get(COLUMN_NAME, cr), colRs.get(DATA_TYPE, cr),
            colRs.get(TYPE_NAME, cr), colRs.get(COLUMN_SIZE, cr),
            colRs.get(BUFFER_LENGTH, cr), colRs.get(DECIMAL_DIGITS, cr),
            itoa(DatabaseMetaData.bestRowNotPseudo)
          );
        }
      });
    });
    return out;
  }

  public static L4Result dbGetImportedKeys(String tablePattern, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, NULL AS PKTABLE_NAME, NULL AS PKCOLUMN_NAME, ",
      "         NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, NULL AS FKTABLE_NAME, NULL AS FKCOLUMN_NAME, ",
      "         0 AS KEY_SEQ, 0 AS UPDATE_RULE, 0 AS DELETE_RULE, ",
      "         NULL AS FK_NAME, NULL AS PK_NAME, 0 AS DEFERRABILITY",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_INTEGER, RQ_INTEGER, RQ_INTEGER,
      RQ_VARCHAR, RQ_VARCHAR, RQ_INTEGER
    );
    var tables = tablePattern.equals(All) ? dbUserTables(client) : List.of(tablePattern);
    for (var table : tables) {
      var fkTable = quote(table);
      var rs = client.querySingle(format("PRAGMA foreign_key_list('%s')", fkTable)).first();
      rs.forEach((i, row) -> {
        var seq = Integer.toString(Integer.parseInt(rs.get(kSeq, row)) + 1);
        var fkCol = rs.get(kFrom, row);
        var pkCol = rs.get(kTo, row);
        var pkTable = rs.get(kTable, row);
        var onDelete = rs.get(kOnDelete, row);
        var updateRule = itoa(DatabaseMetaData.importedKeyNoAction);
        var deleteRule = itoa(
          onDelete != null && onDelete.equals(CASCADE)
            ? DatabaseMetaData.importedKeyCascade
            : DatabaseMetaData.importedKeyNoAction
        );
        out.addRow(
          Main, null, pkTable, pkCol,
          Main, null, fkTable, fkCol,
          seq, updateRule, deleteRule,
          format("FK_%s_%s", fkTable, fkCol),
          format("PK_%s", pkTable),
          itoa(DatabaseMetaData.importedKeyInitiallyDeferred)
        );
      });
    }
    return out;
  }

  public static L4Result dbGetExportedKeys(String table, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, NULL AS PKTABLE_NAME, NULL AS PKCOLUMN_NAME, ",
      "         NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, NULL AS FKTABLE_NAME, NULL AS FKCOLUMN_NAME, ",
      "         0 AS KEY_SEQ, 0 AS UPDATE_RULE, 0 AS DELETE_RULE, ",
      "         NULL AS FK_NAME, NULL AS PK_NAME, 0 AS DEFERRABILITY",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_SMALLINT, RQ_SMALLINT, RQ_SMALLINT,
      RQ_VARCHAR, RQ_VARCHAR, RQ_INTEGER
    );
    // Find all tables with foreign keys referencing this table
    var tables = dbGetTables(null, new String[] { TABLE }, client);
    tables.forEach((i, row) -> {
      var fkTable = quote(tables.get(TABLE_NAME, row));
      var fkRs = client.querySingle(format("PRAGMA foreign_key_list('%s')", fkTable)).first();
      fkRs.forEach((j, row0) -> {
        if (table.equals(fkRs.get(kTable, row0))) {
          var seq = itoa(atoi(fkRs.get(kSeq, row0)) + 1);
          var fkCol = fkRs.get(kFrom, row0);
          var pkCol = fkRs.get(kTo, row0);
          var onDelete = fkRs.get(kOnDelete, row0);
          var updateRule = itoa(DatabaseMetaData.importedKeyNoAction);
          var deleteRule = itoa(
            onDelete != null && onDelete.equals(CASCADE)
              ? DatabaseMetaData.importedKeyCascade
              : DatabaseMetaData.importedKeyNoAction
          );
          out.addRow(
            Main, null, table, pkCol,
            Main, null, fkTable, fkCol,
            seq, updateRule, deleteRule,
            format("FK_%s_%s", fkTable, fkCol),
            format("PK_%s", table),
            itoa(DatabaseMetaData.importedKeyInitiallyDeferred)
          );
        }
      });
    });
    return out;
  }

  public static L4Result dbGetCrossReference(String parentTable, String foreignTable, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, NULL AS PKTABLE_NAME, NULL AS PKCOLUMN_NAME, ",
      "         NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, NULL AS FKTABLE_NAME, NULL AS FKCOLUMN_NAME, ",
      "         0 AS KEY_SEQ, 0 AS UPDATE_RULE, 0 AS DELETE_RULE, ",
      "         NULL AS FK_NAME, NULL AS PK_NAME, 0 AS DEFERRABILITY",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_SMALLINT, RQ_SMALLINT, RQ_SMALLINT,
      RQ_VARCHAR, RQ_VARCHAR, RQ_SMALLINT
    );
    var fkRs = dbGetImportedKeys(foreignTable, client);
    fkRs.forEach((i, row) -> {
      var pkTable = fkRs.get(PKTABLE_NAME, row);
      if (parentTable.equals(pkTable)) {
        out.addRow(
          Main, null, pkTable, fkRs.get(PKCOLUMN_NAME, row),
          Main, null, fkRs.get(FKTABLE_NAME, row), fkRs.get(FKCOLUMN_NAME, row),
          fkRs.get(KEY_SEQ, row),
          fkRs.get(UPDATE_RULE, row),
          fkRs.get(DELETE_RULE, row),
          fkRs.get(FK_NAME, row),
          fkRs.get(PK_NAME, row),
          fkRs.get(DEFERRABILITY, row)
        );
      }
    });
    return out;
  }

  public static L4Result dbGetTypeInfo(L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS TYPE_NAME, 0 AS DATA_TYPE, 0 AS PRECISION, ",
      "         NULL AS LITERAL_PREFIX, NULL AS LITERAL_SUFFIX, ",
      "         NULL AS CREATE_PARAMS, 0 AS NULLABLE, FALSE AS CASE_SENSITIVE, ",
      "         0 AS SEARCHABLE, FALSE AS UNSIGNED_ATTRIBUTE, FALSE AS FIXED_PREC_SCALE, ",
      "         FALSE AS AUTO_INCREMENT, NULL AS LOCAL_TYPE_NAME, 0 AS MINIMUM_SCALE, ",
      "         0 AS MAXIMUM_SCALE, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, ",
      "         0 AS NUM_PREC_RADIX",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_INTEGER, RQ_INTEGER,
      RQ_VARCHAR, RQ_VARCHAR,
      RQ_VARCHAR, RQ_SMALLINT, RQ_BOOLEAN,
      RQ_SMALLINT, RQ_BOOLEAN, RQ_BOOLEAN,
      RQ_BOOLEAN, RQ_VARCHAR, RQ_SMALLINT,
      RQ_SMALLINT, RQ_INTEGER, RQ_INTEGER,
      RQ_INTEGER
    );
    for (var rqt : RQ_TYPES) {
      var jt = getJdbcType(rqt);
      var hasPrefix = anyOf(jt, Types.VARCHAR, Types.DATE, Types.TIME, Types.TIMESTAMP, Types.DATALINK, Types.NVARCHAR);
      out.addRow(
        rqt, itoa(jt), itoa(getJdbcTypePrecision(rqt)),
        hasPrefix ? "'" : null, hasPrefix ? "'" : null,
        null, itoa(DatabaseMetaData.typeNullable), btoa(false),
        itoa(3), btoa(true), btoa(false),
        btoa(false), rqt, itoa(0),
        itoa(0), itoa(0), itoa(0),
        itoa(10)
      );
    }
    return out;
  }

  public static L4Result dbGetIndexInfo(String tablePattern, boolean unique, L4Client client) {
    var out = client.querySingle(join("\n", "",
      "SELECT * FROM (",
      "  SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, ",
      "         FALSE AS NON_UNIQUE, NULL AS INDEX_QUALIFIER, NULL AS INDEX_NAME, 0 AS TYPE, ",
      "         0 AS ORDINAL_POSITION, NULL AS COLUMN_NAME, NULL AS ASC_OR_DESC, 0 AS CARDINALITY, ",
      "         0 AS PAGES, NULL AS FILTER_CONDITION",
      ") WHERE 1 = 0"
    )).first().setTypes(
      RQ_VARCHAR, RQ_VARCHAR, RQ_VARCHAR,
      RQ_BOOLEAN, RQ_VARCHAR, RQ_VARCHAR, RQ_SMALLINT,
      RQ_INTEGER, RQ_VARCHAR, RQ_VARCHAR, RQ_BIGINT,
      RQ_BIGINT, RQ_VARCHAR
    );
    var tables = tablePattern.equals(All) ? dbUserTables(client) : List.of(tablePattern);
    for (var table : tables) {
      var seq = new int[] { 1 };
      var ti = client.querySingle(format("PRAGMA table_info('%s')", quote(table))).first();
      ti.forEach((i, row) -> {
        var colName = ti.get(kName, row);
        var isPk = atoi(ti.get(kPk, row)) == 1;
        if (isPk) {
          out.addRow(
            Main, null, table,
            btoa(false), null, format("PK_IDX_%s", table), itoa(DatabaseMetaData.tableIndexOther),
            itoa(seq[0]++), colName, "A", itoa(0),
            itoa(0), null
          );
        }
      });
      var rs = client.querySingle(format("PRAGMA index_list('%s')", quote(table))).first();
      rs.forEach((i, row) -> {
        var indexName = rs.get(kName, row);
        var isUnique = atoi(rs.get(kUnique, row)) == 1;
        var iexInfo = client.querySingle(format("PRAGMA index_xinfo('%s')", quote(indexName))).first();
        iexInfo.forEach((j, row0) -> {
          var skip = unique && !isUnique;
          if (!skip && (atoi(iexInfo.get(kCid, row0)) != -1)) {
            var colName = iexInfo.get(kName, row0);
            var colSort = new String[1];
            var desc = atob(iexInfo.get(kDesc, row0));
            colSort[0] = desc ? "D" : "A";
            out.addRow(
              Main, null, table,
              btoa(!isUnique), null, indexName, itoa(DatabaseMetaData.tableIndexOther),
              itoa(seq[0]++), colName, colSort[0], itoa(0),
              itoa(0), null
            );
          }
        });
      });
    }
    return out;
  }

}
