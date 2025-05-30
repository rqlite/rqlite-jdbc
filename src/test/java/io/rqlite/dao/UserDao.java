package io.rqlite.dao;

import io.vacco.metolithe.core.MtCaseFormat;
import io.vacco.metolithe.core.MtDescriptor;
import io.vacco.metolithe.core.MtIdFn;
import io.vacco.metolithe.core.MtWriteDao;

import org.codejargon.fluentjdbc.api.FluentJdbc;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**************************************************
 * Generated source file. Do not modify directly. *
 **************************************************/
public class UserDao extends MtWriteDao<io.rqlite.schema.User, java.lang.Integer> {

  public static final String fld_uid = "uid";
  public static final String fld_email = "email";
  public static final String fld_nickName = "nickName";
  
  public UserDao(String schema, MtCaseFormat fmt, FluentJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.User.class, fmt), idFn);
  }
  
  public Collection<io.rqlite.schema.User> loadWhereUidEq(java.lang.Integer uid) {
    return loadWhereEq(fld_uid, uid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.User>> loadWhereUidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_uid, values);
  }

  public long deleteWhereUidEq(java.lang.Integer uid) {
    return deleteWhereEq(fld_uid, uid);
  }
  
  public Collection<io.rqlite.schema.User> loadWhereEmailEq(java.lang.String email) {
    return loadWhereEq(fld_email, email);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.User>> loadWhereEmailIn(java.lang.String ... values) {
    return loadWhereIn(fld_email, values);
  }

  public long deleteWhereEmailEq(java.lang.String email) {
    return deleteWhereEq(fld_email, email);
  }
  
  public Collection<io.rqlite.schema.User> loadWhereNickNameEq(java.lang.String nickName) {
    return loadWhereEq(fld_nickName, nickName);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.User>> loadWhereNickNameIn(java.lang.String ... values) {
    return loadWhereIn(fld_nickName, values);
  }

  public long deleteWhereNickNameEq(java.lang.String nickName) {
    return deleteWhereEq(fld_nickName, nickName);
  }
  
}
