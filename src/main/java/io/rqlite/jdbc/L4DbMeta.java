package io.rqlite.jdbc;

import io.rqlite.client.L4Client;

import java.sql.*;
import java.util.*;

import static io.rqlite.jdbc.L4Block.*;
import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Db.*;
import static io.rqlite.jdbc.L4Jdbc.*;

public class L4DbMeta implements DatabaseMetaData {

  public static String DriverName = "rqlite";

  private final L4Client  client;
  private final L4Conn    conn;
  private String          sqliteVersion;

  public L4DbMeta(L4Client client, L4Conn conn) {
    this.client = Objects.requireNonNull(client);
    this.conn = conn;
  }

  private ResultSet executeQuery(String sql) throws SQLException {
    return new L4St(client, conn).executeQuery(sql);
  }

  // General Database Properties

  @Override public boolean allProceduresAreCallable() {
    return false; // Rqlite/SQLite does not support stored procedures
  }

  @Override public boolean allTablesAreSelectable() {
    return true; // All tables can be queried by default in SQLite
  }

  @Override public String getURL() {
    return client.getBaseUrl();
  }

  @Override public String getUserName() {
    return client.basicAuthUser;
  }

  @Override public boolean isReadOnly() throws SQLException {
    try (var rs = executeQuery("PRAGMA writable_schema")) {
      if (rs.next()) {
        return rs.getInt(1) == 0; // 0 means read-only
      }
      return false; // Default to writable
    }
  }

  @Override public boolean nullsAreSortedHigh() {
    return false; // SQLite sorts NULLs low by default
  }

  @Override public boolean nullsAreSortedLow() {
    return true; // SQLite sorts NULLs low
  }

  @Override public boolean nullsAreSortedAtStart() {
    return true; // SQLite sorts NULLs at the start (low)
  }

  @Override public boolean nullsAreSortedAtEnd() {
    return false; // SQLite does not sort NULLs at the end
  }

  @Override public String getDatabaseProductName() {
    return "SQLite";
  }

  @Override public String getDatabaseProductVersion() throws SQLException {
    if (sqliteVersion != null) {
      return sqliteVersion;
    }
    try (var rs = executeQuery("SELECT sqlite_version()")) {
      if (rs.next()) {
        var ver = rs.getString(1);
        this.sqliteVersion = ver;
        return ver;
      }
      return "unknown";
    }
  }

  @Override public String getDriverName() {
    return DriverName;
  }

  /**
   * Sets a custom driver name to override the default value reported by {@link #getDriverName()}.
   * <p />
   * This method is provided for compatibility with certain ORM frameworks or libraries that
   * perform strict checks on the reported database product name (e.g., expecting "SQLite JDBC"
   * for SQLite-compatible drivers). Use this to specify an alternative name if needed for
   * integration purposes.
   *
   * @param driverNameOverride the custom driver name to use
   */
  public static void setDriverName(String driverNameOverride) {
    DriverName = driverNameOverride;
  }

  @Override public String getDriverVersion() {
    return driverVersion();
  }

  @Override public int getDriverMajorVersion() {
    return driverVersionMajor();
  }

  @Override public int getDriverMinorVersion() {
    return driverVersionMinor();
  }

  @Override public boolean usesLocalFiles() {
    return false; // Rqlite is distributed, not local file-based
  }

  @Override public boolean usesLocalFilePerTable() {
    return false; // Rqlite does not use per-table files
  }

  @Override public boolean supportsMixedCaseIdentifiers() {
    return true; // SQLite is case-sensitive for identifiers
  }

  @Override public boolean storesUpperCaseIdentifiers() {
    return false; // SQLite preserves case
  }

  @Override public boolean storesLowerCaseIdentifiers() {
    return false; // SQLite preserves case
  }

  @Override public boolean storesMixedCaseIdentifiers() {
    return true; // SQLite preserves mixed case
  }

  @Override public boolean supportsMixedCaseQuotedIdentifiers() {
    return true; // SQLite supports quoted identifiers with mixed case
  }

  @Override public boolean storesUpperCaseQuotedIdentifiers() {
    return false; // SQLite preserves case in quotes
  }

  @Override public boolean storesLowerCaseQuotedIdentifiers() {
    return false; // SQLite preserves case in quotes
  }

  @Override public boolean storesMixedCaseQuotedIdentifiers() {
    return true; // SQLite preserves mixed case in quotes
  }

  @Override public String getIdentifierQuoteString() {
    return "\""; // SQLite uses double quotes for identifiers
  }

  @Override public String getSQLKeywords() {
    return Keywords;
  }

  @Override public String getNumericFunctions() {
    return FnNumeric;
  }

  @Override public String getStringFunctions() {
    return FnString;
  }

  @Override public String getSystemFunctions() {
    return FnSystem;
  }

  @Override public String getTimeDateFunctions() {
    return FnDateTime;
  }

  @Override public String getSearchStringEscape() {
    return "\\"; // SQLite uses backslash for LIKE escape
  }

  @Override public String getExtraNameCharacters() {
    return ""; // SQLite allows standard alphanumeric and underscores
  }

  // SQL Feature Support

  @Override public boolean supportsAlterTableWithAddColumn() {
    return true; // SQLite supports ALTER TABLE ADD COLUMN
  }

  @Override public boolean supportsAlterTableWithDropColumn() {
    return false; // SQLite does not support DROP COLUMN
  }

  @Override public boolean supportsColumnAliasing() {
    return true; // SQLite supports AS for column aliases
  }

  @Override public boolean nullPlusNonNullIsNull() {
    return true; // SQLite follows SQL standard for NULL arithmetic
  }

  @Override public boolean supportsConvert() {
    return false; // SQLite does not support CONVERT function
  }

  @Override public boolean supportsConvert(int fromType, int toType) {
    return false; // No CONVERT support
  }

  @Override public boolean supportsTableCorrelationNames() {
    return true; // SQLite supports table aliases
  }

  @Override public boolean supportsDifferentTableCorrelationNames() {
    return true; // SQLite allows different correlation names
  }

  @Override public boolean supportsExpressionsInOrderBy() {
    return true; // SQLite supports expressions in ORDER BY
  }

  @Override public boolean supportsOrderByUnrelated() {
    return true; // SQLite allows ORDER BY on non-selected columns
  }

  @Override public boolean supportsGroupBy() {
    return true; // SQLite supports GROUP BY
  }

  @Override public boolean supportsGroupByUnrelated() {
    return true; // SQLite allows GROUP BY on non-selected columns
  }

  @Override public boolean supportsGroupByBeyondSelect() {
    return true; // SQLite allows GROUP BY with non-selected columns
  }

  @Override public boolean supportsLikeEscapeClause() {
    return true; // SQLite supports ESCAPE in LIKE
  }

  @Override public boolean supportsMultipleResultSets() {
    return false; // SQLite does not support multiple result sets
  }

  @Override public boolean supportsMultipleTransactions() {
    return true; // SQLite supports multiple transactions
  }

  @Override public boolean supportsNonNullableColumns() {
    return true; // SQLite supports NOT NULL constraints
  }

  @Override public boolean supportsMinimumSQLGrammar() {
    return true; // SQLite supports basic SQL
  }

  @Override public boolean supportsCoreSQLGrammar() {
    return true; // SQLite supports core SQL features
  }

  @Override public boolean supportsExtendedSQLGrammar() {
    return false; // SQLite has limited extended SQL support
  }

  @Override public boolean supportsANSI92EntryLevelSQL() {
    return true; // SQLite supports ANSI-92 entry level
  }

  @Override public boolean supportsANSI92IntermediateSQL() {
    return false; // SQLite does not fully support intermediate level
  }

  @Override public boolean supportsANSI92FullSQL() {
    return false; // SQLite does not support full ANSI-92
  }

  @Override public boolean supportsIntegrityEnhancementFacility() {
    return true; // SQLite supports foreign keys and constraints
  }

  @Override public boolean supportsOuterJoins() {
    return true; // SQLite supports LEFT OUTER JOIN
  }

  @Override public boolean supportsFullOuterJoins() {
    return false; // SQLite does not support FULL OUTER JOIN
  }

  @Override public boolean supportsLimitedOuterJoins() {
    return true; // SQLite supports LEFT OUTER JOIN
  }

  @Override public String getSchemaTerm() {
    return "schema"; // Not used in SQLite, but provide term
  }

  @Override public String getProcedureTerm() {
    return "procedure"; // Not supported, but provide term
  }

  @Override public String getCatalogTerm() {
    return "database"; // SQLite uses databases, not catalogs
  }

  @Override public boolean isCatalogAtStart() {
    return true; // SQLite database name precedes table name
  }

  @Override public String getCatalogSeparator() {
    return "."; // SQLite uses dot for database.table
  }

  @Override public boolean supportsSchemasInDataManipulation() {
    return false; // SQLite does not support schemas
  }

  @Override public boolean supportsSchemasInProcedureCalls() {
    return false; // No stored procedures
  }

  @Override public boolean supportsSchemasInTableDefinitions() {
    return false; // No schemas
  }

  @Override public boolean supportsSchemasInIndexDefinitions() {
    return false; // No schemas
  }

  @Override public boolean supportsSchemasInPrivilegeDefinitions() {
    return false; // No schemas
  }

  @Override public boolean supportsCatalogsInDataManipulation() {
    return true; // SQLite supports database name in queries
  }

  @Override public boolean supportsCatalogsInProcedureCalls() {
    return false; // No stored procedures
  }

  @Override public boolean supportsCatalogsInTableDefinitions() {
    return true; // SQLite supports database name in table definitions
  }

  @Override public boolean supportsCatalogsInIndexDefinitions() {
    return true; // SQLite supports database name in index definitions
  }

  @Override public boolean supportsCatalogsInPrivilegeDefinitions() {
    return false; // SQLite does not support privilege definitions
  }

  @Override public boolean supportsPositionedDelete() {
    return false; // SQLite does not support positioned updates/deletes
  }

  @Override public boolean supportsPositionedUpdate() {
    return false; // SQLite does not support positioned updates/deletes
  }

  @Override public boolean supportsSelectForUpdate() {
    return false; // SQLite does not support FOR UPDATE
  }

  @Override public boolean supportsStoredProcedures() {
    return false; // SQLite does not support stored procedures
  }

  @Override public boolean supportsSubqueriesInComparisons() {
    return true; // SQLite supports subqueries in comparisons
  }

  @Override public boolean supportsSubqueriesInExists() {
    return true; // SQLite supports EXISTS subqueries
  }

  @Override public boolean supportsSubqueriesInIns() {
    return true; // SQLite supports IN subqueries
  }

  @Override public boolean supportsSubqueriesInQuantifieds() {
    return true; // SQLite supports quantified subqueries
  }

  @Override public boolean supportsCorrelatedSubqueries() {
    return true; // SQLite supports correlated subqueries
  }

  @Override public boolean supportsUnion() {
    return true; // SQLite supports UNION
  }

  @Override public boolean supportsUnionAll() {
    return true; // SQLite supports UNION ALL
  }

  @Override public boolean supportsOpenCursorsAcrossCommit() {
    return false; // SQLite does not support cursors
  }

  @Override public boolean supportsOpenCursorsAcrossRollback() {
    return false; // SQLite does not support cursors
  }

  @Override public boolean supportsOpenStatementsAcrossCommit() {
    return true; // SQLite allows open statements across commits
  }

  @Override public boolean supportsOpenStatementsAcrossRollback() {
    return true; // SQLite allows open statements across rollbacks
  }

  // Limits and Constraints

  @Override public int getMaxBinaryLiteralLength() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxCharLiteralLength() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnNameLength()  {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnsInGroupBy() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnsInIndex() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnsInOrderBy() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnsInSelect() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxColumnsInTable() {
    return 2000; // SQLite limit
  }

  @Override public int getMaxConnections() {
    return 0; // Rqlite limit depends on configuration, unknown
  }

  @Override public int getMaxCursorNameLength() {
    return 0; // SQLite does not support cursors
  }

  @Override public int getMaxIndexLength() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxSchemaNameLength() {
    return 0; // Schemas not supported
  }

  @Override public int getMaxProcedureNameLength() {
    return 0; // Procedures not supported
  }

  @Override public int getMaxCatalogNameLength() {
    return 0; // No specific limit for database names
  }

  @Override public int getMaxRowSize() {
    return 0; // No specific limit in SQLite
  }

  @Override public boolean doesMaxRowSizeIncludeBlobs() {
    return true; // SQLite includes BLOBs in row size
  }

  @Override public int getMaxStatementLength() {
    return 1000000; // SQLite default limit (1MB)
  }

  @Override public int getMaxStatements() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxTableNameLength() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxTablesInSelect() {
    return 0; // No specific limit in SQLite
  }

  @Override public int getMaxUserNameLength() {
    return 0; // Rqlite does not expose user info
  }

  // Transaction Support

  @Override public int getDefaultTransactionIsolation() {
    return Connection.TRANSACTION_SERIALIZABLE; // SQLite default
  }

  @Override public boolean supportsTransactions() {
    return true; // SQLite supports transactions
  }

  @Override public boolean supportsTransactionIsolationLevel(int level) {
    return level == Connection.TRANSACTION_SERIALIZABLE; // SQLite only supports SERIALIZABLE
  }

  @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() {
    return true; // SQLite supports both in transactions
  }

  @Override public boolean supportsDataManipulationTransactionsOnly() {
    return false; // SQLite supports DDL in transactions
  }

  @Override public boolean dataDefinitionCausesTransactionCommit() {
    return false; // SQLite does not auto-commit DDL
  }

  @Override public boolean dataDefinitionIgnoredInTransactions() {
    return false; // SQLite processes DDL in transactions
  }

  // Metadata Queries

  @Override public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
    // SQLite does not support stored procedures, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS PROCEDURE_CAT, NULL AS PROCEDURE_SCHEM, " +
      "NULL AS PROCEDURE_NAME, NULL AS RESERVED1, NULL AS RESERVED2, NULL AS RESERVED3, " +
      "NULL AS REMARKS, 0 AS PROCEDURE_TYPE, NULL AS SPECIFIC_NAME) WHERE 1=0");
  }

  @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException {
    // SQLite does not support stored procedures, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS PROCEDURE_CAT, NULL AS PROCEDURE_SCHEM, " +
      "NULL AS PROCEDURE_NAME, NULL AS COLUMN_NAME, 0 AS COLUMN_TYPE, 0 AS DATA_TYPE, " +
      "NULL AS TYPE_NAME, 0 AS PRECISION, 0 AS LENGTH, 0 AS SCALE, 0 AS RADIX, " +
      "0 AS NULLABLE, NULL AS REMARKS, NULL AS COLUMN_DEF, 0 AS SQL_DATA_TYPE, " +
      "0 AS SQL_DATETIME_SUB, 0 AS CHAR_OCTET_LENGTH, 0 AS ORDINAL_POSITION, " +
      "NULL AS IS_NULLABLE, NULL AS SPECIFIC_NAME) WHERE 1=0");
  }

  @Override public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
    return sqlRun(() -> new L4Rs(
      dbGetTables(tableNamePattern, types, client), null)
    );
  }

  @Override public ResultSet getSchemas() throws SQLException {
    // SQLite does not support schemas, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TABLE_SCHEM, NULL AS TABLE_CATALOG) WHERE 1=0");
  }

  @Override public ResultSet getCatalogs() throws SQLException {
    // SQLite supports multiple databases, list attached databases
    return sqlRun(() -> new L4Rs(dbGetCatalogs(client), null));
  }

  @Override public ResultSet getTableTypes() throws SQLException {
    // SQLite supports TABLE and VIEW
    return sqlRun(() -> new L4Rs(dbGetTableTypes(client), null));
  }

  @Override public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    return sqlRun(() -> new L4Rs(
      dbGetColumns(tableNamePattern, columnNamePattern, client),
      null
    ));
  }

  @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
    // SQLite does not support column privileges, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, " +
      "NULL AS COLUMN_NAME, NULL AS GRANTOR, NULL AS GRANTEE, NULL AS PRIVILEGE, NULL AS IS_GRANTABLE) WHERE 1=0");
  }

  @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    // SQLite does not support table privileges, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, " +
      "NULL AS GRANTOR, NULL AS GRANTEE, NULL AS PRIVILEGE, NULL AS IS_GRANTABLE) WHERE 1=0");
  }

  @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetBestRowIdentifier(table, nullable, client), null));
  }

  @Override public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
    // SQLite does not support version columns, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS SCOPE, NULL AS COLUMN_NAME, 0 AS DATA_TYPE, " +
      "NULL AS TYPE_NAME, 0 AS COLUMN_SIZE, 0 AS BUFFER_LENGTH, 0 AS DECIMAL_DIGITS, " +
      "0 AS PSEUDO_COLUMN) WHERE 1=0");
  }

  @Override public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetPrimaryKeys(table, client), null));
  }

  @Override public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetImportedKeys(table, client), null));
  }

  @Override public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetExportedKeys(table, client), null));
  }

  @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
                                               String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetCrossReference(parentTable, foreignTable, client), null));
  }

  @Override public ResultSet getTypeInfo() throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetTypeInfo(client), null));
  }

  @Override public ResultSet getIndexInfo(String catalog, String schema, String table,
                                          boolean unique, boolean approximate) throws SQLException {
    return sqlRun(() -> new L4Rs(dbGetIndexInfo(table, unique, client), null));
  }

  // ResultSet Support

  @Override public boolean supportsResultSetType(int type) {
    return type == ResultSet.TYPE_FORWARD_ONLY; // SQLite only supports forward-only
  }

  @Override public boolean supportsResultSetConcurrency(int type, int concurrency) {
    return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
  }

  @Override public boolean ownUpdatesAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean ownDeletesAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean ownInsertsAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean othersUpdatesAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean othersDeletesAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean othersInsertsAreVisible(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean updatesAreDetected(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean deletesAreDetected(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean insertsAreDetected(int type) {
    return false; // SQLite does not support updatable result sets
  }

  @Override public boolean supportsBatchUpdates() {
    return true; // Rqlite supports batch updates
  }

  @Override public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
    // SQLite does not support UDTs, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, NULL AS TYPE_NAME, " +
      "NULL AS CLASS_NAME, 0 AS DATA_TYPE, NULL AS REMARKS, 0 AS BASE_TYPE) WHERE 1=0");
  }

  @Override public Connection getConnection() throws SQLException {
    if (conn == null) {
      throw badQuery("No connection available");
    }
    return conn;
  }

  // Advanced Features

  @Override public boolean supportsSavepoints() {
    return true; // SQLite supports SAVEPOINT
  }

  @Override public boolean supportsNamedParameters() {
    return true; // Rqlite supports ? and :name parameters
  }

  @Override public boolean supportsMultipleOpenResults() {
    return false; // SQLite does not support multiple open result sets
  }

  @Override public boolean supportsGetGeneratedKeys() {
    return false;
  }

  @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
    // SQLite does not support UDTs, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, NULL AS TYPE_NAME, " +
      "NULL AS SUPERTYPE_CAT, NULL AS SUPERTYPE_SCHEM, NULL AS SUPERTYPE_NAME) WHERE 1=0");
  }

  @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
    // SQLite does not support table inheritance, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, " +
      "NULL AS SUPERTABLE_NAME) WHERE 1=0");
  }

  @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
    // SQLite does not support UDTs, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, NULL AS TYPE_NAME, " +
      "NULL AS ATTR_NAME, 0 AS DATA_TYPE, NULL AS ATTR_TYPE_NAME, 0 AS ATTR_SIZE, " +
      "0 AS DECIMAL_DIGITS, 0 AS NUM_PREC_RADIX, 0 AS NULLABLE, NULL AS REMARKS, " +
      "NULL AS ATTR_DEF, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, 0 AS CHAR_OCTET_LENGTH, " +
      "0 AS ORDINAL_POSITION, NULL AS IS_NULLABLE, NULL AS SCOPE_CATALOG, NULL AS SCOPE_SCHEMA, " +
      "NULL AS SCOPE_TABLE, 0 AS SOURCE_DATA_TYPE) WHERE 1=0");
  }

  @Override public boolean supportsResultSetHoldability(int holdability) {
    return holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT; // SQLite closes cursors at commit
  }

  @Override public int getResultSetHoldability() {
    return ResultSet.CLOSE_CURSORS_AT_COMMIT;
  }

  @Override public int getDatabaseMajorVersion() throws SQLException {
    var version = getDatabaseProductVersion();
    return Integer.parseInt(version.split("\\.")[0]);
  }

  @Override public int getDatabaseMinorVersion() throws SQLException {
    var version = getDatabaseProductVersion();
    var parts = version.split("\\.");
    return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
  }

  @Override public int getJDBCMajorVersion() {
    return 4; // JDBC 4.0
  }

  @Override public int getJDBCMinorVersion() {
    return 0; // JDBC 4.0
  }

  @Override public int getSQLStateType() {
    return DatabaseMetaData.sqlStateSQL; // Use standard SQL state codes
  }

  @Override public boolean locatorsUpdateCopy() {
    return false; // SQLite does not support locators
  }

  @Override public boolean supportsStatementPooling() {
    return false; // SQLite does not support statement pooling
  }

  @Override public RowIdLifetime getRowIdLifetime() {
    return RowIdLifetime.ROWID_UNSUPPORTED; // SQLite does not support ROWID as per JDBC
  }

  @Override public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
    return getSchemas();
  }

  @Override public boolean supportsStoredFunctionsUsingCallSyntax() {
    return false; // SQLite does not support stored functions
  }

  @Override public boolean autoCommitFailureClosesAllResultSets() {
    return false; // SQLite does not close result sets on auto-commit failure
  }

  @Override public ResultSet getClientInfoProperties() throws SQLException {
    // No client info properties supported
    return executeQuery("SELECT * FROM (SELECT NULL AS NAME, 0 AS MAX_LEN, NULL AS DEFAULT_VALUE, " +
      "NULL AS DESCRIPTION) WHERE 1=0");
  }

  @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
    // SQLite does not support stored functions
    return executeQuery("SELECT * FROM (SELECT NULL AS FUNCTION_CAT, NULL AS FUNCTION_SCHEM, " +
      "NULL AS FUNCTION_NAME, NULL AS REMARKS, 0 AS FUNCTION_TYPE, NULL AS SPECIFIC_NAME) WHERE 1=0");
  }

  @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
    // No stored functions, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS FUNCTION_CAT, NULL AS FUNCTION_SCHEM, " +
      "NULL AS FUNCTION_NAME, NULL AS COLUMN_NAME, 0 AS COLUMN_TYPE, 0 AS DATA_TYPE, " +
      "NULL AS TYPE_NAME, 0 AS PRECISION, 0 AS LENGTH, 0 AS SCALE, 0 AS RADIX, " +
      "0 AS NULLABLE, NULL AS REMARKS, 0 AS CHAR_OCTET_LENGTH, 0 AS ORDINAL_POSITION, " +
      "NULL AS IS_NULLABLE, NULL AS SPECIFIC_NAME) WHERE 1=0");
  }

  @Override public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
    // SQLite does not support pseudo columns, return empty result set
    return executeQuery("SELECT * FROM (SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, NULL AS TABLE_NAME, " +
      "NULL AS COLUMN_NAME, 0 AS DATA_TYPE, 0 AS COLUMN_SIZE, 0 AS DECIMAL_DIGITS, " +
      "0 AS NUM_PREC_RADIX, NULL AS COLUMN_USAGE, NULL AS REMARKS, 0 AS CHAR_OCTET_LENGTH, " +
      "NULL AS IS_NULLABLE) WHERE 1=0");
  }

  @Override public boolean generatedKeyAlwaysReturned() {
    return false;
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    if (iface == DatabaseMetaData.class || iface == Wrapper.class) {
      return iface.cast(this);
    }
    throw badUnwrap(iface);
  }

  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
    if (iface == null) {
      throw badInterface();
    }
    return iface == DatabaseMetaData.class || iface == Wrapper.class;
  }

}
