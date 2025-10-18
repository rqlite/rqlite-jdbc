package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Statement;
import io.rqlite.jdbc.L4Db;
import io.rqlite.jdbc.L4DbMeta;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.sql.*;
import java.util.*;

import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.jdbc.L4Db.*;
import static j8spec.J8Spec.*;
import static java.lang.String.join;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4DbMetaTest {

  private static final L4Client rq = L4Tests.localClient();
  private static L4DbMeta meta;

  // Helper method to read ResultSet data into a List of Maps for easier validation
  private static List<Map<String, Object>> readResultSet(ResultSet rs) throws SQLException {
    var rows = new ArrayList<Map<String, Object>>();
    var rsMeta = rs.getMetaData();
    int columnCount = rsMeta.getColumnCount();
    while (rs.next()) {
      var row = new HashMap<String, Object>();
      for (int i = 1; i <= columnCount; i++) {
        var rqt = rs.getMetaData().getColumnTypeName(i);
        var clazz = getJdbcTypeClass(rqt);
        row.put(rsMeta.getColumnLabel(i), rs.getObject(i, clazz));
      }
      rows.add(row);
    }
    rs.close();
    return rows;
  }

  private static void setupTables() {
    // Drop existing tables
    rq.executeSingle("DROP TABLE IF EXISTS related_table");
    rq.executeSingle("DROP TABLE IF EXISTS metadata_table");

    // Create metadata_table table (same as L4RsTest)
    var createTable = join("\n", "",
      "CREATE TABLE metadata_table (",
      "  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,",
      "  num_val NUMERIC,",
      "  bool_val BOOLEAN,",
      "  tiny_val TINYINT,",
      "  small_val SMALLINT,",
      "  int_val INTEGER,",
      "  big_val BIGINT,",
      "  float_val FLOAT,",
      "  double_val DOUBLE,",
      "  text_val VARCHAR,",
      "  date_val DATE,",
      "  time_val TIME,",
      "  ts_val TIMESTAMP,",
      "  url_val DATALINK,",
      "  clob_val CLOB,",
      "  nclob_val NCLOB,",
      "  nstring_val NVARCHAR,",
      "  blob_val BLOB",
      ")"
    );
    var res = rq.executeSingle(createTable);
    assertEquals(200, res.statusCode);

    var createIndex = "CREATE UNIQUE INDEX metadata_index ON metadata_table(small_val)";
    assertEquals(200, rq.executeSingle(createIndex).statusCode);

    // Create related_table with foreign key
    var createRelatedTable = join("\n", "",
      "CREATE TABLE related_table (",
      "  rel_id INTEGER PRIMARY KEY AUTOINCREMENT,",
      "  test_id INTEGER,",
      "  data VARCHAR,",
      "  FOREIGN KEY (test_id) REFERENCES metadata_table(id)",
      ")"
    );
    res = rq.executeSingle(createRelatedTable);
    assertEquals(200, res.statusCode);

    // Insert sample data
    var insertSql = join("", "",
      "INSERT INTO metadata_table (",
      "  num_val, bool_val, tiny_val, small_val, int_val, big_val, float_val, double_val,",
      "  text_val, date_val, time_val, ts_val, url_val, clob_val, nclob_val, nstring_val, blob_val",
      ") VALUES (",
      "  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
      ")"
    );
    var blobData = Base64.getEncoder().encodeToString("Hello, rqlite!".getBytes());
    res = rq.execute(
      true,
      new L4Statement().sql(insertSql).withPositionalParams(
        123.45, 1, 127, 32767, 2147483647, 9223372036854775807L, 3.14f, 2.71828,
        "Hello, world!", "2023-10-15", "14:30:00", "2023-10-15 14:30:00",
        "https://example.com", "This is a CLOB", "This is an NCLOB",
        "This is an NSTRING", blobData
      )
    );
    assertEquals(200, res.statusCode);

    var insertRelatedSql = "INSERT INTO related_table (test_id, data) VALUES (?, ?)";
    res = rq.execute(
      true,
      new L4Statement().sql(insertRelatedSql).withPositionalParams(1, "Related data")
    );
    assertEquals(200, res.statusCode);
  }

  static {
    if (L4Tests.runIntegrationTests) {
      beforeAll(() -> {
        meta = new L4DbMeta(rq, null);
        setupTables();
      });

      // Database Identification
      it("Tests database identification methods", () -> {
        assertEquals("SQLite", meta.getDatabaseProductName());
        assertTrue(meta.getDatabaseProductVersion().matches("\\d+\\.\\d+\\.\\d+"));
        assertEquals(4, meta.getJDBCMajorVersion());
        assertEquals(0, meta.getJDBCMinorVersion());
        L4DbMeta.setDriverName(L4DbMeta.DriverName);
        assertEquals(L4DbMeta.DriverName, meta.getDriverName());
        assertTrue(meta.getDriverVersion().startsWith("9."));
        assertEquals(9, meta.getDriverMajorVersion());
        assertTrue(meta.getDriverMinorVersion() != -1);
        assertEquals(DatabaseMetaData.sqlStateSQL, meta.getSQLStateType());
      });

      // User and URL
      it("Tests user and URL methods", () -> {
        assertTrue(meta.getUserName().isEmpty());
        assertEquals(L4Tests.RQLITE_URL, meta.getURL());
      });

      // Database Properties
      it("Tests database property methods", () -> {
        assertFalse(meta.isReadOnly()); // rqlite is not read-only
        assertFalse(meta.allProceduresAreCallable()); // No procedures
        assertTrue(meta.allTablesAreSelectable()); // All tables are selectable
        assertTrue(meta.doesMaxRowSizeIncludeBlobs()); // SQLite includes BLOBs in row size
        assertEquals(RowIdLifetime.ROWID_UNSUPPORTED, meta.getRowIdLifetime());
      });

      // SQL Keywords and Identifiers
      it("Tests SQL keywords and identifier methods", () -> {
        assertEquals(L4Db.Keywords, meta.getSQLKeywords());
        assertEquals("\\", meta.getSearchStringEscape());
        assertEquals("", meta.getExtraNameCharacters());
        assertEquals("schema", meta.getSchemaTerm());
        assertEquals("procedure", meta.getProcedureTerm());
        assertEquals("database", meta.getCatalogTerm());
        assertEquals(".", meta.getCatalogSeparator());
        assertTrue(meta.isCatalogAtStart());
      });

      // Function Lists
      it("Tests function list methods", () -> {
        assertEquals(L4Db.FnNumeric, meta.getNumericFunctions());
        assertEquals(L4Db.FnString, meta.getStringFunctions());
        assertEquals(L4Db.FnSystem, meta.getSystemFunctions());
        assertEquals(L4Db.FnDateTime, meta.getTimeDateFunctions());
      });

      // SQL Feature Support
      it("Tests SQL feature support methods", () -> {
        assertTrue(meta.supportsAlterTableWithAddColumn());
        assertFalse(meta.supportsAlterTableWithDropColumn());
        assertTrue(meta.supportsColumnAliasing());
        assertTrue(meta.nullPlusNonNullIsNull());
        assertFalse(meta.supportsConvert());
        assertFalse(meta.supportsConvert(Types.INTEGER, Types.VARCHAR));
        assertTrue(meta.supportsTableCorrelationNames());
        assertTrue(meta.supportsDifferentTableCorrelationNames());
        assertTrue(meta.supportsExpressionsInOrderBy());
        assertTrue(meta.supportsOrderByUnrelated());
        assertTrue(meta.supportsGroupBy());
        assertTrue(meta.supportsGroupByUnrelated());
        assertTrue(meta.supportsGroupByBeyondSelect());
        assertTrue(meta.supportsLikeEscapeClause());
        assertFalse(meta.supportsMultipleResultSets());
        assertTrue(meta.supportsMultipleTransactions());
        assertTrue(meta.supportsNonNullableColumns());
        assertTrue(meta.supportsMinimumSQLGrammar());
        assertTrue(meta.supportsCoreSQLGrammar());
        assertFalse(meta.supportsExtendedSQLGrammar());
        assertTrue(meta.supportsANSI92EntryLevelSQL());
        assertFalse(meta.supportsANSI92IntermediateSQL());
        assertFalse(meta.supportsANSI92FullSQL());
        assertTrue(meta.supportsIntegrityEnhancementFacility());
        assertTrue(meta.supportsOuterJoins());
        assertFalse(meta.supportsFullOuterJoins());
        assertTrue(meta.supportsLimitedOuterJoins());
        assertFalse(meta.supportsSchemasInDataManipulation());
        assertFalse(meta.supportsSchemasInProcedureCalls());
        assertFalse(meta.supportsSchemasInTableDefinitions());
        assertFalse(meta.supportsSchemasInIndexDefinitions());
        assertFalse(meta.supportsSchemasInPrivilegeDefinitions());
        assertTrue(meta.supportsCatalogsInDataManipulation());
        assertFalse(meta.supportsCatalogsInProcedureCalls());
        assertTrue(meta.supportsCatalogsInTableDefinitions());
        assertTrue(meta.supportsCatalogsInIndexDefinitions());
        assertFalse(meta.supportsCatalogsInPrivilegeDefinitions());
        assertFalse(meta.supportsPositionedDelete());
        assertFalse(meta.supportsPositionedUpdate());
        assertFalse(meta.supportsSelectForUpdate());
        assertFalse(meta.supportsStoredProcedures());
        assertTrue(meta.supportsSubqueriesInComparisons());
        assertTrue(meta.supportsSubqueriesInExists());
        assertTrue(meta.supportsSubqueriesInIns());
        assertTrue(meta.supportsSubqueriesInQuantifieds());
        assertTrue(meta.supportsCorrelatedSubqueries());
        assertTrue(meta.supportsUnion());
        assertTrue(meta.supportsUnionAll());
        assertFalse(meta.supportsOpenCursorsAcrossCommit());
        assertFalse(meta.supportsOpenCursorsAcrossRollback());
        assertTrue(meta.supportsOpenStatementsAcrossCommit());
        assertTrue(meta.supportsOpenStatementsAcrossRollback());
        assertTrue(meta.supportsSavepoints());
        assertTrue(meta.supportsNamedParameters());
        assertFalse(meta.supportsMultipleOpenResults());
        assertFalse(meta.supportsGetGeneratedKeys());
        assertFalse(meta.supportsStoredFunctionsUsingCallSyntax());
        assertFalse(meta.autoCommitFailureClosesAllResultSets());
        assertTrue(meta.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertTrue(meta.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
        assertFalse(meta.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
        assertFalse(meta.ownUpdatesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.ownDeletesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.ownInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersUpdatesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersDeletesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.updatesAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.deletesAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.insertsAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertTrue(meta.supportsBatchUpdates());
        assertTrue(meta.supportsResultSetHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT));
        assertFalse(meta.supportsResultSetHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT));
        assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, meta.getResultSetHoldability());
        assertFalse(meta.locatorsUpdateCopy());
        assertFalse(meta.supportsStatementPooling());
        assertFalse(meta.generatedKeyAlwaysReturned());
      });

      // Limits and Constraints
      it("Tests limit and constraint methods", () -> {
        assertEquals(0, meta.getMaxBinaryLiteralLength());
        assertEquals(0, meta.getMaxCharLiteralLength());
        assertEquals(0, meta.getMaxColumnNameLength());
        assertEquals(0, meta.getMaxColumnsInGroupBy());
        assertEquals(0, meta.getMaxColumnsInIndex());
        assertEquals(0, meta.getMaxColumnsInOrderBy());
        assertEquals(0, meta.getMaxColumnsInSelect());
        assertEquals(2000, meta.getMaxColumnsInTable());
        assertEquals(0, meta.getMaxConnections());
        assertEquals(0, meta.getMaxCursorNameLength());
        assertEquals(0, meta.getMaxIndexLength());
        assertEquals(0, meta.getMaxSchemaNameLength());
        assertEquals(0, meta.getMaxProcedureNameLength());
        assertEquals(0, meta.getMaxCatalogNameLength());
        assertEquals(0, meta.getMaxRowSize());
        assertEquals(1000000, meta.getMaxStatementLength());
        assertEquals(0, meta.getMaxStatements());
        assertEquals(0, meta.getMaxTableNameLength());
        assertEquals(0, meta.getMaxTablesInSelect());
        assertEquals(0, meta.getMaxUserNameLength());
      });

      // Transaction Support
      it("Tests transaction support methods", () -> {
        assertEquals(Connection.TRANSACTION_SERIALIZABLE, meta.getDefaultTransactionIsolation());
        assertTrue(meta.supportsTransactions());
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE));
        assertFalse(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED));
        assertTrue(meta.supportsDataDefinitionAndDataManipulationTransactions());
        assertFalse(meta.supportsDataManipulationTransactionsOnly());
        assertFalse(meta.dataDefinitionCausesTransactionCommit());
        assertFalse(meta.dataDefinitionIgnoredInTransactions());
      });

      // Metadata Queries: Procedures and Functions
      it("Tests procedure and function metadata", () -> {
        // getProcedures
        var rs = meta.getProcedures(null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getProcedureColumns
        rs = meta.getProcedureColumns(null, null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getFunctions
        rs = meta.getFunctions(null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getFunctionColumns
        rs = meta.getFunctionColumns(null, null, null, null);
        assertFalse(rs.next());
        rs.close();
      });

      // Metadata Queries: Schemas
      it("Tests schema metadata", () -> {
        var rs = meta.getSchemas();
        assertFalse(rs.next()); // SQLite does not support schemas
        rs.close();
        rs = meta.getSchemas(null, null);
        assertFalse(rs.next());
        rs.close();
      });

      // Metadata Queries: Catalogs
      it("Tests catalog metadata", () -> {
        var rs = meta.getCatalogs();
        var rows = readResultSet(rs);
        assertFalse(rows.isEmpty()); // At least 'main' database
        var foundMain = false;
        for (var row : rows) {
          if (Main.equals(row.get("TABLE_CAT"))) {
            foundMain = true;
            break;
          }
        }
        assertTrue(foundMain);
      });

      // Metadata Queries: Table Types
      it("Tests table type metadata", () -> {
        var rs = meta.getTableTypes();
        var rows = readResultSet(rs);
        assertEquals(2, rows.size());
        var types = new HashSet<String>();
        for (var row : rows) {
          types.add((String) row.get("TABLE_TYPE"));
        }
        assertTrue(types.contains(TABLE));
        assertTrue(types.contains(VIEW));
      });

      // Metadata Queries: Tables
      it("Tests table metadata", () -> {
        var rs = meta.getTables(null, null, "metadata_table", null);
        var rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals(Main, row.get("TABLE_CAT"));
        assertNull(row.get("TABLE_SCHEM"));
        assertEquals("metadata_table", row.get("TABLE_NAME"));
        assertEquals("table", row.get("TABLE_TYPE"));
        assertNull(row.get("REMARKS"));
        assertNull(row.get("TYPE_CAT"));
        assertNull(row.get("TYPE_SCHEM"));
        assertNull(row.get("TYPE_NAME"));
        assertNull(row.get("SELF_REFERENCING_COL_NAME"));
        assertNull(row.get("REF_GENERATION"));

        // Test with specific types
        rs = meta.getTables(null, null, null, new String[]{"TABLE"});
        rows = readResultSet(rs);
        assertTrue(rows.size() >= 2); // metadata_table and related_table
        boolean foundTestData = false;
        for (var row2 : rows) {
          if ("metadata_table".equals(row2.get("TABLE_NAME"))) {
            foundTestData = true;
            break;
          }
        }
        assertTrue(foundTestData);
      });

      // Metadata Queries: Columns
      it("Tests column metadata", () -> {
        var rs = meta.getColumns(null, null, "metadata_table", null);
        var rows = readResultSet(rs);
        assertEquals(18, rows.size()); // 18 columns in metadata_table
        var idColumn = rows.stream()
          .filter(row -> "id".equals(row.get("COLUMN_NAME")))
          .findFirst()
          .orElseThrow(() -> new AssertionError("id column not found"));
        assertEquals(Main, idColumn.get("TABLE_CAT"));
        assertNull(idColumn.get("TABLE_SCHEM"));
        assertEquals("metadata_table", idColumn.get("TABLE_NAME"));
        assertEquals("id", idColumn.get("COLUMN_NAME"));
        assertEquals(Types.INTEGER, idColumn.get("DATA_TYPE"));
        assertEquals("INTEGER", idColumn.get("TYPE_NAME"));
        assertEquals(10, idColumn.get("COLUMN_SIZE"));
        assertEquals(0, idColumn.get("DECIMAL_DIGITS"));
        assertEquals(10, idColumn.get("NUM_PREC_RADIX"));
        assertEquals(DatabaseMetaData.columnNoNulls, idColumn.get("NULLABLE"));
        assertNull(idColumn.get("REMARKS"));
        assertNull(idColumn.get("COLUMN_DEF"));
        assertEquals(10, idColumn.get("CHAR_OCTET_LENGTH"));
        assertEquals(1, idColumn.get("ORDINAL_POSITION"));
        assertEquals(L4Db.NO, idColumn.get("IS_NULLABLE"));
        assertNull(idColumn.get("SCOPE_CATALOG"));
        assertNull(idColumn.get("SCOPE_SCHEMA"));
        assertNull(idColumn.get("SCOPE_TABLE"));
        assertEquals(0, idColumn.get("SOURCE_DATA_TYPE"));
        assertEquals(L4Db.YES, idColumn.get("IS_AUTOINCREMENT"));
        assertEquals(L4Db.NO, idColumn.get("IS_GENERATEDCOLUMN"));
      });

      // Metadata Queries: Privileges
      it("Tests privilege metadata", () -> {
        var rs = meta.getTablePrivileges(null, null, null);
        assertFalse(rs.next()); // SQLite does not support privileges
        rs.close();
        rs = meta.getColumnPrivileges(null, null, "metadata_table", null);
        assertFalse(rs.next());
        rs.close();
      });

      // Metadata Queries: Keys and Indexes
      it("Tests key and index metadata", () -> {
        // getPrimaryKeys
        var rs = meta.getPrimaryKeys(null, null, "metadata_table");
        var rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var pk = rows.get(0);
        assertEquals(Main, pk.get(TABLE_CAT));
        assertNull(pk.get("TABLE_SCHEM"));
        assertEquals("metadata_table", pk.get("TABLE_NAME"));
        assertEquals("id", pk.get("COLUMN_NAME"));
        assertEquals(1, pk.get("KEY_SEQ"));
        assertNotNull(pk.get("PK_NAME"));

        // getBestRowIdentifier
        rs = meta.getBestRowIdentifier(null, null, "metadata_table", DatabaseMetaData.bestRowSession, true);
        rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var bri = rows.get(0);
        assertEquals(DatabaseMetaData.bestRowSession, bri.get("SCOPE"));
        assertEquals("id", bri.get("COLUMN_NAME"));
        assertEquals(Types.INTEGER, bri.get("DATA_TYPE"));
        assertEquals("INTEGER", bri.get("TYPE_NAME"));
        assertEquals(10, bri.get("COLUMN_SIZE"));
        assertEquals(0, bri.get("BUFFER_LENGTH"));
        assertEquals(0, bri.get("DECIMAL_DIGITS"));
        assertEquals(DatabaseMetaData.bestRowNotPseudo, bri.get("PSEUDO_COLUMN"));

        // getImportedKeys
        rs = meta.getImportedKeys(null, null, "related_table");
        rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var fk = rows.get(0);
        assertEquals(Main, fk.get("PKTABLE_CAT"));
        assertNull(fk.get("PKTABLE_SCHEM"));
        assertEquals("metadata_table", fk.get("PKTABLE_NAME"));
        assertEquals("id", fk.get("PKCOLUMN_NAME"));
        assertEquals(Main, fk.get("FKTABLE_CAT"));
        assertNull(fk.get("FKTABLE_SCHEM"));
        assertEquals("related_table", fk.get("FKTABLE_NAME"));
        assertEquals("test_id", fk.get("FKCOLUMN_NAME"));
        assertEquals(1, fk.get("KEY_SEQ"));
        assertEquals(DatabaseMetaData.importedKeyNoAction, fk.get("UPDATE_RULE"));
        assertEquals(DatabaseMetaData.importedKeyNoAction, fk.get("DELETE_RULE"));
        assertNotNull(fk.get("FK_NAME"));
        assertNotNull(fk.get("PK_NAME"));

        // getExportedKeys
        rs = meta.getExportedKeys(null, null, "metadata_table");
        rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var ek = rows.get(0);
        assertEquals(Main, ek.get("PKTABLE_CAT"));
        assertNull(ek.get("PKTABLE_SCHEM"));
        assertEquals("metadata_table", ek.get("PKTABLE_NAME"));
        assertEquals("id", ek.get("PKCOLUMN_NAME"));
        assertEquals(Main, ek.get("FKTABLE_CAT"));
        assertNull(ek.get("FKTABLE_SCHEM"));
        assertEquals("related_table", ek.get("FKTABLE_NAME"));
        assertEquals("test_id", ek.get("FKCOLUMN_NAME"));

        // getCrossReference
        rs = meta.getCrossReference(null, null, "metadata_table", null, null, "related_table");
        rows = readResultSet(rs);
        assertEquals(1, rows.size());
        var cr = rows.get(0);
        assertEquals(Main, cr.get("PKTABLE_CAT"));
        assertEquals("metadata_table", cr.get("PKTABLE_NAME"));
        assertEquals("id", cr.get("PKCOLUMN_NAME"));
        assertEquals(Main, cr.get("FKTABLE_CAT"));
        assertEquals("related_table", cr.get("FKTABLE_NAME"));
        assertEquals("test_id", cr.get("FKCOLUMN_NAME"));

        // getIndexInfo
        rs = meta.getIndexInfo(null, null, "metadata_table", true, false);
        rows = readResultSet(rs);
        assertFalse(rows.isEmpty()); // At least primary key index
        boolean foundPkIndex = false;
        for (var row : rows) {
          if ("small_val".equals(row.get("COLUMN_NAME")) && "metadata_index".equals(row.get("INDEX_NAME"))) {
            foundPkIndex = true;
            assertEquals(Main, row.get("TABLE_CAT"));
            assertNull(row.get("TABLE_SCHEM"));
            assertEquals("metadata_table", row.get("TABLE_NAME"));
            assertFalse((Boolean) row.get("NON_UNIQUE"));
            assertEquals("small_val", row.get("COLUMN_NAME"));
            assertEquals(2, row.get("ORDINAL_POSITION"));
            assertEquals("A", row.get("ASC_OR_DESC"));
            assertEquals(DatabaseMetaData.tableIndexOther, row.get("TYPE"));
            break;
          }
        }
        assertTrue(foundPkIndex);
      });

      // Metadata Queries: Type Info
      it("Tests type info metadata", () -> {
        var rs = meta.getTypeInfo();
        var rows = readResultSet(rs);
        assertTrue(rows.size() >= RQ_TYPES.length);
        var typeNames = new HashSet<String>();
        for (var row : rows) {
          typeNames.add((String) row.get("TYPE_NAME"));
          assertNotNull(row.get("DATA_TYPE"));
          assertNotNull(row.get("PRECISION"));
          assertNotNull(row.get("NULLABLE"));
          assertNotNull(row.get("CASE_SENSITIVE"));
          assertNotNull(row.get("SEARCHABLE"));
          assertNotNull(row.get("UNSIGNED_ATTRIBUTE"));
          assertNotNull(row.get("FIXED_PREC_SCALE"));
          assertNotNull(row.get("AUTO_INCREMENT"));
          assertNotNull(row.get("LOCAL_TYPE_NAME"));
          assertNotNull(row.get("MINIMUM_SCALE"));
          assertNotNull(row.get("MAXIMUM_SCALE"));
          assertNotNull(row.get("SQL_DATA_TYPE"));
          assertNotNull(row.get("SQL_DATETIME_SUB"));
          assertNotNull(row.get("NUM_PREC_RADIX"));
        }
        for (var rqType : RQ_TYPES) {
          assertTrue("Missing type: " + rqType, typeNames.contains(rqType));
        }
      });

      // Metadata Queries: UDTs and Others
      it("Tests UDT and other metadata", () -> {
        // getUDTs
        var rs = meta.getUDTs(null, null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getSuperTypes
        rs = meta.getSuperTypes(null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getSuperTables
        rs = meta.getSuperTables(null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getAttributes
        rs = meta.getAttributes(null, null, null, null);
        assertFalse(rs.next());
        rs.close();
        // getVersionColumns
        rs = meta.getVersionColumns(null, null, "metadata_table");
        assertFalse(rs.next());
        rs.close();
        // getPseudoColumns
        rs = meta.getPseudoColumns(null, null, null, null);
        assertFalse(rs.next());
        rs.close();
      });

      // Metadata Queries: Client Info
      it("Tests client info properties", () -> {
        var rs = meta.getClientInfoProperties();
        assertFalse(rs.next()); // No client info properties
        rs.close();
      });

      // Unwrap and Wrapper
      it("Tests unwrap and wrapper methods", () -> {
        assertTrue(meta.isWrapperFor(DatabaseMetaData.class));
        assertTrue(meta.isWrapperFor(Wrapper.class));
        assertFalse(meta.isWrapperFor(String.class));
        assertSame(meta, meta.unwrap(DatabaseMetaData.class));
        try {
          meta.unwrap(String.class);
          fail("Expected SQLException for invalid unwrap");
        } catch (SQLException e) {
          assertNotNull(e.getMessage());
        }
        try {
          meta.unwrap(null);
          fail("Expected SQLException for null interface");
        } catch (SQLException e) {
          assertNotNull(e.getMessage());
        }
      });

      // Error Handling
      it("Tests error handling for invalid inputs", () -> {
        // Invalid table name
        try {
          meta.getColumns(null, null, "non_existent_table", null);
          var rs = meta.getColumns(null, null, "non_existent_table", null);
          var rows = readResultSet(rs);
          assertEquals(0, rows.size());
        } catch (SQLException e) {
          assertEquals(SqlStateInvalidQuery, e.getSQLState());
        }

        // Invalid column name
        var rs = meta.getColumns(null, null, "metadata_table", "invalid_column");
        assertFalse(rs.next());
        rs.close();
      });
    }
  }
}