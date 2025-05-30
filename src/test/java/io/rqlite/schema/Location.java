package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;

@MtEntity public class Location {

  @MtPk public int lid;

  @MtFk(Device.class)
  @MtUnique(idx = 0, inPk = true)
  public int did;

  @MtVarchar(32) @MtNotNull
  @MtUnique(idx = 1, inPk = true)
  public String geoHash8;

}
