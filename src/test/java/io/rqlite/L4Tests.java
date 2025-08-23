package io.rqlite;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Http;
import io.rqlite.client.L4Options;
import io.rqlite.jdbc.L4Log;
import io.vacco.metolithe.core.MtLog;
import io.vacco.shax.logging.ShOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;

public class L4Tests {

  static {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_LOGLEVEL, "trace");
  }

  public static final Logger log = LoggerFactory.getLogger(L4Tests.class);

  public static void initLogging() {
    L4Log.setTraceLogger(log::trace);
    L4Log.setDebugLogger(log::debug);
    L4Log.setInfoLogger(log::info);
    MtLog.setDebugLogger(log::debug);
    MtLog.setInfoLogger(log::info);
    MtLog.setWarnLogger(log::warn);
  }

  public static final String RQLITE_URL = System.getenv("RQLITE_URL") == null ? "http://localhost:4001" : System.getenv("RQLITE_URL");
  public static final boolean runIntegrationTests = !GraphicsEnvironment.isHeadless() || System.getenv("RQLITE_URL") != null;

  public static L4Client localClient() {
    return new L4Client(RQLITE_URL, L4Http.defaultHttpClient(L4Options.timeoutSec).build());
  }

  public static void setupPreparedStatementTestTable(L4Client rq) {
    var dr = rq.executeSingle("DROP TABLE IF EXISTS ps_test_data");
    assertEquals(200, dr.statusCode);

    var createTable = String.join("\n", "",
      "CREATE TABLE ps_test_data (",
      "  id INTEGER PRIMARY KEY AUTOINCREMENT,",
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
    var res0 = rq.executeSingle(createTable);
    assertEquals(200, res0.statusCode);
  }

}
