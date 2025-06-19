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
public class DeviceDao extends MtWriteDao<io.rqlite.schema.Device, java.lang.Integer> {

  public static final String fld_did = "did";
  public static final String fld_uid = "uid";
  public static final String fld_number = "number";
  
  public DeviceDao(String schema, MtCaseFormat fmt, MtJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.Device.class, fmt), idFn);
  }
  
  public MtFieldDescriptor fld_did() {
    return this.dsc.getField(fld_did);
  }

  public List<io.rqlite.schema.Device> loadWhereDidEq(java.lang.Integer did) {
    return loadWhereEq(fld_did, did);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereDidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_did, values);
  }

  public MtResult<io.rqlite.schema.Device> deleteWhereDidEq(java.lang.Integer did) {
    return deleteWhereEq(fld_did, did);
  }
  
  public MtFieldDescriptor fld_uid() {
    return this.dsc.getField(fld_uid);
  }

  public List<io.rqlite.schema.Device> loadWhereUidEq(java.lang.Integer uid) {
    return loadWhereEq(fld_uid, uid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereUidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_uid, values);
  }

  public MtResult<io.rqlite.schema.Device> deleteWhereUidEq(java.lang.Integer uid) {
    return deleteWhereEq(fld_uid, uid);
  }
  
  public MtFieldDescriptor fld_number() {
    return this.dsc.getField(fld_number);
  }

  public List<io.rqlite.schema.Device> loadWhereNumberEq(java.lang.Integer number) {
    return loadWhereEq(fld_number, number);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Device>> loadWhereNumberIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_number, values);
  }

  public MtResult<io.rqlite.schema.Device> deleteWhereNumberEq(java.lang.Integer number) {
    return deleteWhereEq(fld_number, number);
  }
  
}
