package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;

@MtEntity public class Location {

  @MtPk public int lid;

  @MtFk(Device.class)
  @MtPk(idx = 0)
  public int did;

  @MtVarchar(32) @MtNotNull
  @MtPk(idx = 1)
  public String geoHash8;

}
