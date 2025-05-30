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
public class DeviceDao extends MtWriteDao<io.rqlite.schema.Device, java.lang.Integer> {

  public static final String fld_did = "did";
  public static final String fld_uid = "uid";
  public static final String fld_number = "number";
  
  public DeviceDao(String schema, MtCaseFormat fmt, FluentJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.Device.class, fmt), idFn);
  }
  
  public Collection<io.rqlite.schema.Device> loadWhereDidEq(java.lang.Integer did) {
    return loadWhereEq(fld_did, did);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereDidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_did, values);
  }

  public long deleteWhereDidEq(java.lang.Integer did) {
    return deleteWhereEq(fld_did, did);
  }
  
  public Collection<io.rqlite.schema.Device> loadWhereUidEq(java.lang.Integer uid) {
    return loadWhereEq(fld_uid, uid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereUidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_uid, values);
  }

  public long deleteWhereUidEq(java.lang.Integer uid) {
    return deleteWhereEq(fld_uid, uid);
  }
  
  public Collection<io.rqlite.schema.Device> loadWhereNumberEq(java.lang.Integer number) {
    return loadWhereEq(fld_number, number);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereNumberIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_number, values);
  }

  public long deleteWhereNumberEq(java.lang.Integer number) {
    return deleteWhereEq(fld_number, number);
  }
  
}
