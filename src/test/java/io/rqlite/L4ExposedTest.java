package io.rqlite;

import io.rqlite.jdbc.L4DbMeta;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.jetbrains.exposed.sql.*;
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManagerKt;
import org.jetbrains.exposed.sql.vendors.SQLiteDialect;
import org.junit.runner.RunWith;

import org.jetbrains.exposed.sql.transactions.TransactionManager;

import java.util.function.Function;

import static org.jetbrains.exposed.sql.StdOutSqlLogger.INSTANCE;
import static j8spec.J8Spec.it;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4ExposedTest {

  public static class Users extends Table {
    public final Column<Integer> id = integer("id", null);
    public final Column<String> name = varchar("name", 50, null);
  }

  static {
    if (L4Tests.runIntegrationTests) {
      it("Interacts with rqlite using Exposed", () -> {
        L4Tests.initLogging();
        L4DbMeta.setDriverName("SQLite JDBC");

        Database.Companion.registerJdbcDriver("jdbc:rqlite", "io.rqlite.jdbc.L4Driver", SQLiteDialect.Companion.getDialectName());
        Database.Companion.registerDialect(SQLiteDialect.Companion.getDialectName(), SQLiteDialect::new);

        //  public final fun connect(user: kotlin.String = COMPILED_CODE, password: kotlin.String = COMPILED_CODE, setupConnection: (java.sql.Connection) -> kotlin.Unit = COMPILED_CODE, databaseConfig: org.jetbrains.exposed.sql.DatabaseConfig? = COMPILED_CODE, connectionAutoRegistration: org.jetbrains.exposed.sql.DatabaseConnectionAutoRegistration = COMPILED_CODE, manager: (org.jetbrains.exposed.sql.Database) -> org.jetbrains.exposed.sql.transactions.TransactionManager = COMPILED_CODE): org.jetbrains.exposed.sql.Database { /* compiled code */ }
        Database.Companion.connect("jdbc:rqlite:http://localhost:4001", "io.rqlite.jdbc.L4Driver", "", "", conn -> null, null, , null);

        var users = new Users();

        ThreadLocalTransactionManagerKt.transaction(null, tx -> {
          SchemaUtils.INSTANCE.create(new Users [] {}, true);
          /*
          QueriesKt.insert()
          users.insert(insert -> {
            insert.set(users.id, 0);
            insert.set(users.name, "Alice");
          });
          users.insert(insert -> {
            insert.set(users.id, 1);
            insert.set(users.name, "Bob");
          });
          */
          return null;
        });

        /*
        transaction(() -> {
          addLogger(INSTANCE);

        });

        transaction(() -> {
          addLogger(INSTANCE);
          users.selectAll().forEach(row -> {
            System.out.println(row);
          });
        });
         */
      });
    }
  }
}
