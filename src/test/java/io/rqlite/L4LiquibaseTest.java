package io.rqlite;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.sql.DriverManager;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

/**
 * Verifies that a real change-set tool (Liquibase) can apply a changelog over the driver,
 * exercising its background auto-commit toggling and commit() calls.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4LiquibaseTest {

  static {
    if (L4Tests.runIntegrationTests) {
      it("Applies a Liquibase changelog over the driver", () -> {
        try (var conn = DriverManager.getConnection(L4Tests.rqUrl)) {
          var stmt = conn.createStatement();
          stmt.execute("DROP TABLE IF EXISTS lb_test");
          stmt.execute("DROP TABLE IF EXISTS DATABASECHANGELOG");
          stmt.execute("DROP TABLE IF EXISTS DATABASECHANGELOGLOCK");

          var database = DatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(new JdbcConnection(conn));
          var liquibase = new Liquibase("liquibase/changelog.sql", new ClassLoaderResourceAccessor(), database);
          liquibase.update(new Contexts());

          try (var rs = conn.createStatement().executeQuery("SELECT name FROM lb_test WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("updated", rs.getString(1));
          }
          try (var rs = conn.createStatement().executeQuery("SELECT COUNT(*) AS c FROM DATABASECHANGELOG")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("c"));
          }
        }
      });
    }
  }

}
