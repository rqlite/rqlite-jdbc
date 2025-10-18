package io.rqlite.client;

import java.util.Properties;

import static java.lang.String.format;

public class L4Options {

  public static final String
    kCaCert = "cacert", kInsecure = "insecure",
    kBaseUrl = "baseUrl", kTimeoutSec = "timeoutSec",
    kQueue = "queue", kWait = "wait", kLevel = "level", kLinearizableTimeoutSec = "linearizableTimeoutSec",
    kFreshnessSec = "freshnessSec", kFreshnessStrict = "freshnessStrict",
    kUser = "user", kPassword = "password";

  public static String  baseUrl, user, password, cacert;

  public static boolean insecure;
  public static boolean queue = false;
  public static boolean wait = true;

  public static L4Level level = L4Level.linearizable;
  public static long    linearizableTimeoutSec = 5;
  public static long    timeoutSec = 5;

  public static long    freshnessSec = 5;
  public static boolean freshnessStrict = false;

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

  public static String queryParams(boolean transaction) {
    var pairs = new String[] {
      queue ? kv("queue", true) : null,
      transaction ? kv("transaction", true) : null,
      kv("timings", true),
      kv("timeout", format("%ds", timeoutSec)),
      kv("wait", wait),
      kv("level", level),
      level == L4Level.linearizable ? kv("linearizable_timeout", format("%ds", linearizableTimeoutSec)) : null,
      kv("freshness", format("%ds", freshnessSec)),
      kv("freshness_strict", freshnessStrict)
    };
    var params = String.join("&", filterNulls(pairs));
    return String.format("?%s", params);
  }

  public static String get(Properties p, String k) {
    return (String) p.get(k);
  }

  public static void update(Properties p) {
    try {
      if (p.containsKey(kBaseUrl)) {
        L4Options.baseUrl = get(p, kBaseUrl);
      }
      if (p.containsKey(kTimeoutSec)) {
        L4Options.timeoutSec = Long.parseLong(get(p, kTimeoutSec));
      }
      if (p.containsKey(kQueue)) {
        L4Options.queue = Boolean.parseBoolean(get(p, kQueue));
      }
      if (p.containsKey(kWait)) {
        L4Options.wait = Boolean.parseBoolean(get(p, kWait));
      }
      if (p.containsKey(kLevel)) {
        L4Options.level = L4Level.valueOf(get(p, kLevel).toLowerCase());
      }
      if (p.containsKey(kLinearizableTimeoutSec)) {
        L4Options.linearizableTimeoutSec = Long.parseLong(get(p, kLinearizableTimeoutSec));
      }
      if (p.containsKey(kFreshnessSec)) {
        L4Options.freshnessSec = Long.parseLong(get(p, kFreshnessSec));
      }
      if (p.containsKey(kFreshnessStrict)) {
        L4Options.freshnessStrict = Boolean.parseBoolean(get(p, kFreshnessStrict));
      }
      if (p.containsKey(kUser)) {
        L4Options.user = get(p, kUser);
      }
      if (p.containsKey(kPassword)) {
        L4Options.password = get(p, kPassword);
      }
      if (p.containsKey(kCaCert)) {
        L4Options.cacert = get(p, kCaCert);
      }
      if (p.containsKey(kInsecure)) {
        L4Options.insecure = Boolean.parseBoolean(get(p, kInsecure));
      }
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

}
