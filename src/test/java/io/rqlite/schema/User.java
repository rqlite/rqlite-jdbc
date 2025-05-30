package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;

@MtEntity public class User {

  @MtPk public int uid;

  @MtNotNull
  @MtVarchar(256)
  @MtUnique(idx = 0, inPk = true)
  public String email;

  @MtNotNull
  @MtVarchar(256)
  public String nickName;

}
