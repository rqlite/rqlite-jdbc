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
    kClientCert = "clientCert", kClientKey = "clientKey";

  public String  baseUrl, user, password, cacert, clientCert, clientKey;

  public boolean insecure;
  public boolean queue = false;
  public boolean wait = true;
  public int     retries = 0;
  public boolean redirect = false;

  public L4Level level = L4Level.linearizable;
  public long    linearizableTimeoutSec = 5;
  public long    timeoutSec = 5;
  public long    dbTimeoutSec = 0;

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

  public void update(Properties p) {
    try {
      if (p.containsKey(kBaseUrl)) {
        this.baseUrl = get(p, kBaseUrl);
      }
      if (p.containsKey(kTimeoutSec)) {
        this.timeoutSec = Long.parseLong(get(p, kTimeoutSec));
      }
      if (p.containsKey(kDbTimeoutSec)) {
        this.dbTimeoutSec = Long.parseLong(get(p, kDbTimeoutSec));
      }
      if (p.containsKey(kQueue)) {
        this.queue = Boolean.parseBoolean(get(p, kQueue));
      }
      if (p.containsKey(kWait)) {
        this.wait = Boolean.parseBoolean(get(p, kWait));
      }
      if (p.containsKey(kLevel)) {
        this.level = L4Level.valueOf(get(p, kLevel).toLowerCase());
      }
      if (p.containsKey(kLinearizableTimeoutSec)) {
        this.linearizableTimeoutSec = Long.parseLong(get(p, kLinearizableTimeoutSec));
      }
      if (p.containsKey(kFreshnessSec)) {
        this.freshnessSec = Long.parseLong(get(p, kFreshnessSec));
      }
      if (p.containsKey(kFreshnessStrict)) {
        this.freshnessStrict = Boolean.parseBoolean(get(p, kFreshnessStrict));
      }
      if (p.containsKey(kRetries)) {
        this.retries = Integer.parseInt(get(p, kRetries));
      }
      if (p.containsKey(kRedirect)) {
        this.redirect = Boolean.parseBoolean(get(p, kRedirect));
      }
      if (p.containsKey(kUser)) {
        this.user = get(p, kUser);
      }
      if (p.containsKey(kPassword)) {
        this.password = get(p, kPassword);
      }
      if (p.containsKey(kCaCert)) {
        this.cacert = get(p, kCaCert);
      }
      if (p.containsKey(kClientCert)) {
        this.clientCert = get(p, kClientCert);
      }
      if (p.containsKey(kClientKey)) {
        this.clientKey = get(p, kClientKey);
      }
      if (p.containsKey(kInsecure)) {
        this.insecure = Boolean.parseBoolean(get(p, kInsecure));
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

}
