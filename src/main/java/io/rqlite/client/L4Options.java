package io.rqlite.client;

import java.util.Properties;

import static java.lang.String.format;

public class L4Options {

  public static final String
    kCaCert = "cacert", kInsecure = "insecure",
    kBaseUrl = "baseUrl", kTimeoutSec = "timeoutSec",
    kQueue = "queue", kWait = "wait", kLevel = "level", kLinearizableTimeoutSec = "linearizableTimeoutSec",
    kFreshnessSec = "freshnessSec", kFreshnessStrict = "freshnessStrict",
    kUser = "user", kPassword = "password", kDbTimeoutSec = "dbTimeoutSec",
    kRetries = "retries", kRedirect = "redirect",
    kClientCert = "clientCert", kClientKey = "clientKey",
    kMaxBatchStatements = "maxBatchStatements";

  public String  baseUrl, user, password, cacert, clientCert, clientKey;

  public boolean insecure;
  public boolean queue = false;
  public boolean wait = true;
  public int     retries = 0;
  public boolean redirect = false;
  public int     maxBatchStatements = 0;

  public L4Level level = L4Level.linearizable;
  public long    linearizableTimeoutSec = 5;
  public volatile long timeoutSec = 5;
  public volatile long dbTimeoutSec = 0;

  public long    freshnessSec = 5;
  public boolean freshnessStrict = false;

  public L4Options() {}

  public L4Options(Properties p) {
    update(p);
  }

  private static String kv(String key, Object value) {
    return String.format("%s=%s", key, value.toString());
  }

  public static String[] filterNulls(String[] input) {
    if (input == null) {
      return new String[0];
    }
    int nonNullCount = 0;
    for (var s : input) {
      if (s != null) {
        nonNullCount++;
      }
    }
    var result = new String[nonNullCount];
    int index = 0;
    for (var s : input) {
      if (s != null) {
        result[index++] = s;
      }
    }
    return result;
  }

  public String queryParams(boolean transaction) {
    var pairs = new String[] {
      queue ? kv("queue", true) : null,
      transaction ? kv("transaction", true) : null,
      timeoutSec > 0 ? kv("timeout", format("%ds", timeoutSec)) : null,
      wait ? kv("wait", true) : null,
      kv("level", level),
      level == L4Level.linearizable && linearizableTimeoutSec > 0
        ? kv("linearizable_timeout", format("%ds", linearizableTimeoutSec)) : null,
      freshnessSec > 0 ? kv("freshness", format("%ds", freshnessSec)) : null,
      freshnessStrict ? kv("freshness_strict", true) : null,
      dbTimeoutSec > 0 ? kv("db_timeout", format("%ds", dbTimeoutSec)) : null,
      retries > 0 ? kv("retries", retries) : null,
      redirect ? kv("redirect", true) : null
    };
    var params = String.join("&", filterNulls(pairs));
    return String.format("?%s", params);
  }

  public static String get(Properties p, String k) {
    return (String) p.get(k);
  }

  /**
   * Case-insensitive property lookup. Keys are matched exactly first, then
   * case-insensitively (JDBC URL query-param keys may be lower-cased).
   */
  private static String getCI(Properties p, String key) {
    var direct = p.getProperty(key);
    if (direct != null) {
      return direct;
    }
    for (var name : p.stringPropertyNames()) {
      if (name.equalsIgnoreCase(key)) {
        return p.getProperty(name);
      }
    }
    return null;
  }

  public void update(Properties p) {
    if (p == null) {
      return;
    }
    try {
      var v = getCI(p, kBaseUrl);
      if (v != null) this.baseUrl = v;
      v = getCI(p, kTimeoutSec);
      if (v != null) this.timeoutSec = Long.parseLong(v);
      v = getCI(p, kDbTimeoutSec);
      if (v != null) this.dbTimeoutSec = Long.parseLong(v);
      v = getCI(p, kQueue);
      if (v != null) this.queue = Boolean.parseBoolean(v);
      v = getCI(p, kWait);
      if (v != null) this.wait = Boolean.parseBoolean(v);
      v = getCI(p, kLevel);
      if (v != null) this.level = L4Level.valueOf(v.toLowerCase());
      v = getCI(p, kLinearizableTimeoutSec);
      if (v != null) this.linearizableTimeoutSec = Long.parseLong(v);
      v = getCI(p, kFreshnessSec);
      if (v != null) this.freshnessSec = Long.parseLong(v);
      v = getCI(p, kFreshnessStrict);
      if (v != null) this.freshnessStrict = Boolean.parseBoolean(v);
      v = getCI(p, kRetries);
      if (v != null) this.retries = Integer.parseInt(v);
      v = getCI(p, kRedirect);
      if (v != null) this.redirect = Boolean.parseBoolean(v);
      v = getCI(p, kMaxBatchStatements);
      if (v != null) this.maxBatchStatements = Integer.parseInt(v);
      v = getCI(p, kUser);
      if (v != null) this.user = v;
      v = getCI(p, kPassword);
      if (v != null) this.password = v;
      v = getCI(p, kCaCert);
      if (v != null) this.cacert = v;
      v = getCI(p, kClientCert);
      if (v != null) this.clientCert = v;
      v = getCI(p, kClientKey);
      if (v != null) this.clientKey = v;
      v = getCI(p, kInsecure);
      if (v != null) this.insecure = Boolean.parseBoolean(v);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

}
