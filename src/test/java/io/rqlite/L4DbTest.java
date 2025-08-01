package io.rqlite;

import io.rqlite.client.L4Client;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static io.rqlite.jdbc.L4Db.*;
import static j8spec.J8Spec.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4DbTest {

  private static final L4Client rq = L4Tests.localClient();

  static {
    if (L4Tests.runIntegrationTests) {
      it("Retrieves basic DB metadata", () -> {
        var o = System.out;
        dbGetTables(null, null, rq).print(o);
        dbGetTables("%", null, rq).print(o);
        dbGetTableTypes(rq).print(o);
        dbGetColumns(null, null, rq).print(o);
        dbGetPrimaryKeys("users", rq).print(o); // TODO this test needs pre-initialized tables
        dbGetBestRowIdentifier("users", false, rq).print(o);
        dbGetTypeInfo(rq).print(o);
        dbGetIndexInfo("%", false, rq).print(System.out);
      });
    }
  }
}
