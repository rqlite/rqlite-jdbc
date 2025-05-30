package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;

@MtEntity public class Device {

  @MtPk public int did;

  @MtFk(User.class)
  @MtUnique(idx = 0, inPk = true)
  public int uid;

  @MtField
  @MtUnique(idx = 1, inPk = true)
  public int number;

}
