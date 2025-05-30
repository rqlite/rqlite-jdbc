package io.rqlite;

import com.zaxxer.hikari.*;
import io.rqlite.dao.DeviceDao;
import io.rqlite.dao.LocationDao;
import io.rqlite.dao.UserDao;
import io.rqlite.jdbc.L4Db;
import io.rqlite.jdbc.L4Log;
import io.rqlite.jdbc.L4Rs;
import io.rqlite.client.L4Client;
import io.rqlite.schema.Device;
import io.rqlite.schema.Location;
import io.rqlite.schema.User;
import io.vacco.metolithe.codegen.dao.MtDaoMapper;
import io.vacco.metolithe.core.*;
import io.vacco.shax.logging.ShOption;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.codejargon.fluentjdbc.api.FluentJdbcBuilder;
import org.codejargon.fluentjdbc.api.integration.ConnectionProvider;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.io.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Stream;

import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4DriverTest {

  public static final Class<?>[] schema = new Class<?>[] {
    User.class, Device.class, Location.class
  };

  public static final String rqUrl = "jdbc:sqlite:http://localhost:4001";
  public static final L4Client rq = L4Tests.localClient();

  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_LOGLEVEL, "trace");

    if (!GraphicsEnvironment.isHeadless()) {
      it("Generates schema DAOs", () -> {
        var daoDir = new File("./src/test/java");
        var pkg = "io.rqlite.dao";
        new MtDaoMapper().mapSchema(daoDir, pkg, MtCaseFormat.KEEP_CASE, schema);
      });
      it("Applies Liquibase changesets",  () -> {
        var log = LoggerFactory.getLogger(L4DriverTest.class);
        L4Log.traceFn = log::trace;
        var hkConfig = new HikariConfig();
        hkConfig.setJdbcUrl(rqUrl);
        try (var hds = new HikariDataSource(hkConfig)) {
          try (var jdbcConn = new JdbcConnection(hds.getConnection())) {
            var ra = new ClassLoaderResourceAccessor();
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConn);
            database.setDefaultCatalogName(L4Db.Main);
            Scope.child(Scope.Attr.resourceAccessor, ra, () -> {
              var commandScope = new CommandScope("update");
              commandScope.addArgumentValue("changelogFile", "l4-schema.yml");
              commandScope.addArgumentValue("database", database);
              commandScope.execute();
            });
          }
        }
      });
      it("Inserts data via object mapping", () -> {
        var Fmt = MtCaseFormat.KEEP_CASE;
        var idFn = new MtMurmur3IFn(1984);
        var connP = (ConnectionProvider) query -> {
          try (var conn = DriverManager.getConnection(rqUrl)) {
            query.receive(conn);
          }
        };
        var fj = new FluentJdbcBuilder().connectionProvider(connP).build();
        var userDao = new UserDao(L4Db.Main, Fmt, fj, idFn);
        var deviceDao = new DeviceDao(L4Db.Main, Fmt, fj, idFn);
        var locationDao = new LocationDao(L4Db.Main, Fmt, fj, idFn);

        var user = new User();
        user.email = "joe@me.com";
        user.nickName = "Joe";
        user = userDao.upsert(user);

        user = new User();
        user.email = "jane@me.com";
        user.nickName = "Jane";
        user = userDao.upsert(user);

        var res = userDao.sql().query()
          .batch("update User set nickName = ? where email = ?")
          .params(Stream.of(
            List.of("JoeLol", "joe@me.com"),
            List.of("JaneLol", "jane@me.com")
          )).run();

        var device = new Device();
        device.number = 4567345;
        device.uid = user.uid;
        device = deviceDao.upsert(device);

        var loc = new Location();
        loc.did = device.did;
        loc.geoHash8 = "9q4gu1y4";
        locationDao.upsert(loc);

        try (var conn = DriverManager.getConnection(rqUrl)) {
          var dbm = conn.getMetaData();
          try (var lol = (L4Rs) dbm.getPrimaryKeys(null, null, "User")) {
            lol.result.print(System.out);
          }
        }
      });
      it("Queries table metadata", () -> {
        var tables = new String[] { "User", "Device", "Location" };
        try (var conn = DriverManager.getConnection(rqUrl)) {
          for (var table : tables) {
            var idx = (L4Rs) conn.getMetaData().getIndexInfo(null, null, table, true, false);
            var cols = L4Db.dbGetColumns(table, null, rq);
            var pk = L4Db.dbGetPrimaryKeys(table, rq);
            var fkImp = L4Db.dbGetImportedKeys(table, rq);
            var fkExp = L4Db.dbGetExportedKeys(table, rq);
            idx.result.print(System.out);
            cols.print(System.out);
            pk.print(System.out);
            fkImp.print(System.out);
            fkExp.print(System.out);
          }
        }
      });
    }
  }
}
