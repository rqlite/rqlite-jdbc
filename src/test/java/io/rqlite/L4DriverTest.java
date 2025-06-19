package io.rqlite;

import com.zaxxer.hikari.*;
import io.rqlite.dao.*;
import io.rqlite.jdbc.*;
import io.rqlite.client.L4Client;
import io.rqlite.schema.*;
import io.vacco.metolithe.changeset.*;
import io.vacco.metolithe.changeset.MtMapper;
import io.vacco.metolithe.core.*;
import io.vacco.metolithe.dao.MtDaoMapper;
import io.vacco.metolithe.id.MtMurmur3IFn;
import io.vacco.metolithe.query.*;
import io.vacco.shax.logging.ShOption;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import java.awt.*;
import java.io.*;
import java.sql.*;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4DriverTest {

  public static final Class<?>[] schema = new Class<?>[] {
    User.class, Device.class, Location.class
  };

  public static final String        rqUrl = "jdbc:sqlite:http://localhost:4001";
  public static final L4Client      rq = L4Tests.localClient();
  public static final MtCaseFormat  Fmt = MtCaseFormat.KEEP_CASE;
  public static final MtConn        connFn = () -> {
    try {
      return DriverManager.getConnection(rqUrl);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  };

  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_LOGLEVEL, "trace");

    if (!GraphicsEnvironment.isHeadless()) {
      it("Generates schema DAOs", () -> {
        var daoDir = new File("./src/test/java");
        var pkg = "io.rqlite.dao";
        new MtDaoMapper().mapSchema(daoDir, pkg, Fmt, schema);
      });
      it("Applies Liquibase changesets",  () -> {
        var log = LoggerFactory.getLogger(L4DriverTest.class);
        L4Log.debugFn = log::debug;
        L4Log.traceFn = log::trace;
        MtLog.setInfoLogger(log::info);
        MtLog.setDebugLogger(log::debug);
        MtLog.setWarnLogger(log::warn);

        var ctx = "integration-test";
        var tables = new MtMapper().build(Fmt, schema);
        var changes = new MtLogMapper(L4Db.Main).process(tables, MtLevel.TABLE_COMPACT);
        for (var chg : changes) {
          chg.source = L4DriverTest.class.getCanonicalName();
          chg.context = ctx;
        }

        var hkConfig = new HikariConfig();
        hkConfig.setJdbcUrl(rqUrl);
        try (var ds = new HikariDataSource(hkConfig)) {
          try (var conn = ds.getConnection()) {
            new MtApply(conn, L4Db.Main)
              .withAutoCommit(true)
              .applyChanges(changes, ctx);
          }
        }
      });
      it("Inserts data via object mapping", () -> {
        var idFn = new MtMurmur3IFn(1984);
        var fj = new MtJdbc().withSupplier(connFn);
        var userDao = new UserDao(L4Db.Main, Fmt, fj, idFn);
        var deviceDao = new DeviceDao(L4Db.Main, Fmt, fj, idFn);
        var locationDao = new LocationDao(L4Db.Main, Fmt, fj, idFn);

        var joe = User.of("joe@me.com", "Joe");
        assertNotNull(userDao.upsert(joe).cmd);
        var jane = User.of("jane@me.com", "Jane");
        jane = userDao.upsert(jane).rec;

        var res0 = userDao.updateLater(User.of("joe@me.com",  "JoeLol"));
        var res1 = userDao.updateLater(User.of("jane@me.com", "JaneLol"));
        userDao.sql().batch(resList -> {
          resList.add(res0);
          resList.add(res1);
        });

        var res2 = userDao.saveLater(User.of("steve@me.com", "Steve"));
        var res3 = userDao.saveLater(User.of("linda@me.com", "Linda"));
        userDao.sql().transaction((connFn, conn) -> {
          res2.on(conn);
          res3.on(conn);
        });

        assertEquals(1, userDao.loadWhereNickNameEq("JoeLol").size());
        assertEquals(1, userDao.loadWhereNickNameEq("JaneLol").size());
        assertEquals(1, userDao.loadWhereNickNameEq("Steve").size());
        assertEquals(1, userDao.loadWhereNickNameEq("Linda").size());

        var device = new Device();
        device.number = 4567345;
        device.uid = jane.uid;
        device = deviceDao.upsert(device).rec;

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
        try (var conn = connFn.get()) {
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
