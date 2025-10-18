package io.rqlite;

import io.rqlite.jdbc.L4DbMeta;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.jetbrains.exposed.sql.*;
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager;
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManagerKt;
import org.jetbrains.exposed.sql.vendors.SQLiteDialect;
import org.junit.runner.RunWith;
import java.util.ServiceLoader;

import static j8spec.J8Spec.it;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4ExposedTest {

  public static class UsersKt extends Table {
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

        Database.Companion.connect(
          "jdbc:rqlite:http://localhost:4001", "io.rqlite.jdbc.L4Driver",
          "", "", conn -> null, null,
          ServiceLoader
            .load(DatabaseConnectionAutoRegistration.class, Database.class.getClassLoader())
            .findFirst().orElseThrow(),
          db -> new ThreadLocalTransactionManager(db, (conn, ti) -> {
            conn.setAutoCommit(false);
            return null;
          })
        );

        var users = new UsersKt();

        ThreadLocalTransactionManagerKt.transaction(null, tx -> {
          SchemaUtils.INSTANCE.create(new UsersKt[] { users }, false);
          QueriesKt.insert(users, (p0, insert) -> {
            insert.set(users.id, 0);
            insert.set(users.name, "Alice");
            return null;
          });
          QueriesKt.insert(users, (p0, insert) -> {
            insert.set(users.id, 1);
            insert.set(users.name, "bob");
            return null;
          });
          return null;
        });
        ThreadLocalTransactionManagerKt.transaction(null, tx -> {
          QueriesKt.selectAll(users).forEach(System.out::println);
          return null;
        });
      });

      // Kotlin reference
      /*
      object Users : Table() {
        val id = integer("id")
        val name = varchar("name", 50)
      }

      fun main() {
        L4Tests.initLogging()
        L4DbMeta.setDriverName("SQLite JDBC")
        Database.registerJdbcDriver("jdbc:rqlite", "io.rqlite.jdbc.L4Driver", SQLiteDialect.dialectName)
        Database.registerDialect(SQLiteDialect.dialectName) { SQLiteDialect() }
        Database.connect(url = "jdbc:rqlite:http://localhost:4001", driver = "io.rqlite.jdbc.L4Driver")
        transaction {
          addLogger(StdOutSqlLogger)
          SchemaUtils.create(Users)
          Users.insert { it[id] = 0; it[name] = "Alice" }
          Users.insert { it[id] = 1; it[name] = "Bob"   }
        }
        transaction {
          addLogger(StdOutSqlLogger)
          Users.selectAll().forEach {
            println(it)
          }
        }
      } */
    }
  }
}
