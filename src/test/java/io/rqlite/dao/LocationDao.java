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
public class LocationDao extends MtWriteDao<io.rqlite.schema.Location, java.lang.Integer> {

  public static final String fld_lid = "lid";
  public static final String fld_did = "did";
  public static final String fld_geoHash8 = "geoHash8";
  
  public LocationDao(String schema, MtCaseFormat fmt, FluentJdbc jdbc, MtIdFn<java.lang.Integer> idFn) {
    super(schema, jdbc, new MtDescriptor<>(io.rqlite.schema.Location.class, fmt), idFn);
  }
  
  public Collection<io.rqlite.schema.Location> loadWhereLidEq(java.lang.Integer lid) {
    return loadWhereEq(fld_lid, lid);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Location>> loadWhereLidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_lid, values);
  }

  public long deleteWhereLidEq(java.lang.Integer lid) {
    return deleteWhereEq(fld_lid, lid);
  }
  
  public Collection<io.rqlite.schema.Location> loadWhereDidEq(java.lang.Integer did) {
    return loadWhereEq(fld_did, did);
  }

  public final Map<java.lang.Integer, List<io.rqlite.schema.Location>> loadWhereDidIn(java.lang.Integer ... values) {
    return loadWhereIn(fld_did, values);
  }

  public long deleteWhereDidEq(java.lang.Integer did) {
    return deleteWhereEq(fld_did, did);
  }
  
  public Collection<io.rqlite.schema.Location> loadWhereGeoHash8Eq(java.lang.String geoHash8) {
    return loadWhereEq(fld_geoHash8, geoHash8);
  }

  public final Map<java.lang.String, List<io.rqlite.schema.Location>> loadWhereGeoHash8In(java.lang.String ... values) {
    return loadWhereIn(fld_geoHash8, values);
  }

  public long deleteWhereGeoHash8Eq(java.lang.String geoHash8) {
    return deleteWhereEq(fld_geoHash8, geoHash8);
  }
  
}
