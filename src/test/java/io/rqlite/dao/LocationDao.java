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
public class LocationDao extends MtWriteDao<io.rqlite.schema.Location, java.lang.Integer> {

  public static final String fld_lid = "lid";
  public static final String fld_did = "did";
  public static final String fld_geoHash8 = "geoHash8";
  
  public LocationDao(String schema, MtCaseFormat fmt, MtJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.Location.class, fmt), idFn);
  }
  
  public MtFieldDescriptor fld_lid() {
    return this.dsc.getField(fld_lid);
  }

  public List<io.rqlite.schema.Location> loadWhereLidEq(java.lang.Integer lid) {
    return loadWhereEq(fld_lid, lid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Location>> loadWhereLidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_lid, values);
  }

  public MtResult<io.rqlite.schema.Location> deleteWhereLidEq(java.lang.Integer lid) {
    return deleteWhereEq(fld_lid, lid);
  }
  
  public MtFieldDescriptor fld_did() {
    return this.dsc.getField(fld_did);
  }

  public List<io.rqlite.schema.Location> loadWhereDidEq(java.lang.Integer did) {
    return loadWhereEq(fld_did, did);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Location>> loadWhereDidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_did, values);
  }

  public MtResult<io.rqlite.schema.Location> deleteWhereDidEq(java.lang.Integer did) {
    return deleteWhereEq(fld_did, did);
  }
  
  public MtFieldDescriptor fld_geoHash8() {
    return this.dsc.getField(fld_geoHash8);
  }

  public List<io.rqlite.schema.Location> loadWhereGeoHash8Eq(java.lang.String geoHash8) {
    return loadWhereEq(fld_geoHash8, geoHash8);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.Location>> loadWhereGeoHash8In(java.lang.String ... values) {
    return loadWhereIn(fld_geoHash8, values);
  }

  public MtResult<io.rqlite.schema.Location> deleteWhereGeoHash8Eq(java.lang.String geoHash8) {
    return deleteWhereEq(fld_geoHash8, geoHash8);
  }
  
}
