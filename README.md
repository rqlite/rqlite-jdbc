# rqlite JDBC Driver

This is a minimal (~130KB, zero dependencies),  Type 4 JDBC driver for [rqlite](https://github.com/rqlite/rqlite),
a lightweight, distributed relational database built on top of SQLite.

This driver enables Java applications to interact with `rqlite` over HTTP, supporting standard
JDBC operations like queries, updates, and batch processing in a clustered environment.

## Features

- **JDBC Compliance**: Supports core JDBC APIs, including `Connection`, `Statement`, `PreparedStatement`, and `ResultSet`.
- **Atomic Transactions**: Executes multiple statements atomically using rqlite’s `transaction=true` mode via batch operations.
- **Clustered Environment Support**: Configurable options for read consistency, write queuing, and timeouts to handle rqlite’s distributed nature.
- **Schema and Metadata Access**: Query table metadata, primary keys, foreign keys, and indexes (see [L4DriverTest](./src/test/java/io/rqlite/L4DriverTest.java)).

## Getting Started

### Prerequisites

- Java 11 or higher
- `rqlite` server running (e.g., `http://localhost:4001`)

Install from [Maven Central](https://mvnrepository.com/artifact/io.rqlite/rqlite-jdbc)

    io.rqlite:rqlite-jdbc:[version]

The driver version corresponds to the last known `rqlite` [release](https://github.com/rqlite/rqlite/releases) the driver was tested against, followed by a build version of the driver itself.

## Basic Usage

Connect to an `rqlite` instance and execute queries using standard JDBC APIs.

```
import java.sql.*;

var url = "jdbc:sqlite:http://localhost:4001";

try (Connection conn = DriverManager.getConnection(url)) {
    var stmt = conn.createStatement();
    stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)");

    var ps = conn.prepareStatement("INSERT INTO users (name, age) VALUES (?, ?)");
    ps.setString(1, "Alice");
    ps.setInt(2, 30);
    ps.executeUpdate();

    var rs = stmt.executeQuery("SELECT * FROM users");
    while (rs.next()) {
        System.out.println("ID: " + rs.getInt("id") + ", Name: " + rs.getString("name") + ", Age: " + rs.getInt("age"));
    }
}
```

## Batch Processing for Transactions

`rqlite` executes statements atomically with `transaction=true`. Use batch operations for multi-statement transactions.

Using `Statement`:

```
Statement stmt = conn.createStatement();
stmt.addBatch("INSERT INTO users (name, age) VALUES ('Fiona', 25)");
stmt.addBatch("INSERT INTO users (name, age) VALUES ('Sinead', 28)");
int[] updateCounts = stmt.executeBatch(); // Executes atomically
```

Using `PreparedStatement`:

```
PreparedStatement ps = conn.prepareStatement("INSERT INTO users (name, age) VALUES (?, ?)");
ps.setString(1, "Fiona");
ps.setInt(2, 25);
ps.addBatch();
ps.setString(1, "Sinead");
ps.setInt(2, 28);
ps.addBatch();
int[] updateCounts = ps.executeBatch(); // Executes atomically
```

See [L4PsTest](./src/test/java/io/rqlite/L4PsTest.java) for advanced examples with various data types, streams, and LOBs.

## Configuration Options

Customize the driver’s behavior via JDBC URL parameters, see [L4Options](./src/main/java/io/rqlite/rqlite/L4Options.java). Below are the available options, their defaults, and their purposes.

These options come from `rqlite`'s [Developer Guide](https://rqlite.io/docs/api)

| Property Key                | Type      | Default Value            | Description                                                                 |
|-----------------------------|-----------|--------------------------|-----------------------------------------------------------------------------|
| `baseUrl`                   | `String`  | `null`                   | The base URL of the RQLite server (e.g., `http://localhost:4001`).           |
| `user`                      | `String`  | `null`                   | Username for RQLite server authentication.                                   |
| `password`                  | `String`  | `null`                   | Password for RQLite server authentication.                                   |
| `cacert`                    | `String`  | `null`                   | Path to the CA certificate for SSL/TLS connections.                         |
| `insecure`                  | `boolean` | `false`                  | If `true`, disables SSL/TLS verification (not recommended for production).   |
| `timeoutSec`                | `long`    | `5`                      | Timeout for HTTP requests in seconds.                                       |
| `queue`                     | `boolean` | `false`                  | If `true`, enables queuing of requests on the RQLite server.                |
| `wait`                      | `boolean` | `true`                   | If `true`, waits for the request to be processed by the RQLite leader.      |
| `level`                     | `L4Level` | `L4Level.linearizable`   | Consistency level for queries (`none`, `weak`, `strong`, `linearizable`).   |
| `linearizableTimeoutSec`    | `long`    | `5`                      | Timeout for linearizable consistency queries in seconds.                    |
| `freshnessSec`              | `long`    | `5`                      | Maximum age of data for freshness-based queries in seconds.                 |
| `freshnessStrict`           | `boolean` | `false`                  | If `true`, enforces strict freshness for queries.                           |

Example JDBC URL:

```java
String url = "jdbc:sqlite:http://localhost:4001?timeoutSec=5&level=strong&freshnessSec=1";
```

## Caveats

### Memory Usage

Result sets are held in memory (mapped from rqlite’s JSON responses to JDBC ResultSet). Write queries that return small datasets to avoid memory issues.

### Catalog Support

Only the `main` SQLite database is reported as a catalog to JDBC.

### Transaction Limitations

The driver offers deferred transaction support on `Connection` instances due to `rqlite`'s [Transaction support](https://rqlite.io/docs/api/api/#transactions) conventions, which deviate from the JDBC standard.

To execute multiple SQL statements as a transaction, you have two options.

#### Batch statements

Populate a JDBC batch using [`Statement`](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/Statement.html#addBatch(java.lang.String)) or [`PreparedStatement`](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/PreparedStatement.html#addBatch()), which will get sent with `transaction=true` to the underlying `rqlite` HTTP request.

#### Synthetic/deferred transactions

Call `setAutoCommit(false)` on a [`Connection`](https://docs.oracle.com/en/java/javase/11/docs/api/java.sql/java/sql/Connection.html#setAutoCommit(boolean)), and run `insert`, `update` or `delete` statements.

The execution of these statements will get deferred until you call `commit()`.

This will send all statements to the database as a single batch, appending `transaction=true` to the underlying `rqlite` HTTP request.

This implies that you won't be able to inspect `ResultSet`s, metadata or row counts after executing each statement. A dummy resultset is provided only for compatibility with JDBC semantics.

The only guarantee is that if `commit()` succeeds, then all deferred statements were accepted by the database.

Lastly, make sure that all statements get executed through the same connection where the transaction was initiated.

### Isolation Level

Only `TRANSACTION_SERIALIZABLE` is supported, with `linearizable` read consistency by default. Setting `level=weak` or `level=none` may introduce read inconsistencies.

### Type Mapping

User-defined SQL types (UDTs) are not supported. `getTypeMap` and `setTypeMap` are implemented for compliance but have no effect.

## Contributing

Contributions are welcome! Please submit issues or pull requests to this GitHub repository.

Requires Gradle 8.1 or later.

Create a file with the following content at `~/.gsOrgConfig.json`:

```
{
  "orgConfigUrl": "https://raw.githubusercontent.com/rqlite/rqlite-jdbc/refs/heads/org-config/org-config.json"
}
```

Then run:

```
gradle clean build
```
