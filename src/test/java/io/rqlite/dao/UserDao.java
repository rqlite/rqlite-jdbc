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

  public static final String fld_uid = "uid";
  public static final String fld_email = "email";
  public static final String fld_nickName = "nickName";
  
  public UserDao(String schema, MtCaseFormat fmt, MtJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.User.class, fmt), idFn);
  }
  
  public MtFieldDescriptor fld_uid() {
    return this.dsc.getField(fld_uid);
  }

  public List<io.rqlite.schema.User> loadWhereUidEq(java.lang.Integer uid) {
    return loadWhereEq(fld_uid, uid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.User>> loadWhereUidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_uid, values);
  }

  public MtResult<io.rqlite.schema.User> deleteWhereUidEq(java.lang.Integer uid) {
    return deleteWhereEq(fld_uid, uid);
  }
  
  public MtFieldDescriptor fld_email() {
    return this.dsc.getField(fld_email);
  }

  public List<io.rqlite.schema.User> loadWhereEmailEq(java.lang.String email) {
    return loadWhereEq(fld_email, email);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.User>> loadWhereEmailIn(java.lang.String ... values) {
    return loadWhereIn(fld_email, values);
  }

  public MtResult<io.rqlite.schema.User> deleteWhereEmailEq(java.lang.String email) {
    return deleteWhereEq(fld_email, email);
  }
  
  public MtFieldDescriptor fld_nickName() {
    return this.dsc.getField(fld_nickName);
  }

  public List<io.rqlite.schema.User> loadWhereNickNameEq(java.lang.String nickName) {
    return loadWhereEq(fld_nickName, nickName);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.User>> loadWhereNickNameIn(java.lang.String ... values) {
    return loadWhereIn(fld_nickName, values);
  }

  public MtResult<io.rqlite.schema.User> deleteWhereNickNameEq(java.lang.String nickName) {
    return deleteWhereEq(fld_nickName, nickName);
  }
  
}
