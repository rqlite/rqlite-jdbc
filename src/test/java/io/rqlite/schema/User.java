package io.rqlite.schema;

import io.vacco.metolithe.annotations.*;
import java.util.Objects;

@MtEntity public class User {

  @MtPk public int uid;

  @MtNotNull
  @MtVarchar(256)
  @MtPk(idx = 0)
  public String email;

  @MtNotNull
  @MtVarchar(256)
  public String nickName;

  public static User of(String email, String nickName) {
    var u = new User();
    u.email = Objects.requireNonNull(email);
    u.nickName = Objects.requireNonNull(nickName);
    return u;
  }

}
