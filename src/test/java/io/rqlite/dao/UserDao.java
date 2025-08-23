package io.rqlite.dao;

import io.vacco.metolithe.core.MtCaseFormat;
import io.vacco.metolithe.core.MtDescriptor;
import io.vacco.metolithe.core.MtFieldDescriptor;
import io.vacco.metolithe.id.MtIdFn;
import io.vacco.metolithe.dao.MtWriteDao;
import io.vacco.metolithe.query.MtJdbc;
import io.vacco.metolithe.query.MtResult;

import java.util.List;
import java.util.Map;

/**************************************************
 * Generated source file. Do not modify directly. *
 **************************************************/
public class UserDao extends MtWriteDao<io.rqlite.schema.User, java.lang.Integer> {

  public static final String fld_nickName = "nickName";

  public UserDao(String schema, MtCaseFormat fmt, MtJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.User.class, fmt), idFn);
  }

  public MtFieldDescriptor fld_nickName() {
    return this.dsc.getField(fld_nickName);
  }

  public List<io.rqlite.schema.User> loadWhereNickNameEq(java.lang.String nickName) {
    return loadWhereEq(fld_nickName, nickName);
  }

}