package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;

@MtEntity public class Device {

  @MtPk public int did;

  @MtFk(User.class)
  @MtPk(idx = 0)
  public int uid;

  @MtField
  @MtPk(idx = 1)
  public int number;

}
