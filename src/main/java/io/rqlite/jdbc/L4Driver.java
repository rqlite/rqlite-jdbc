package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Http;
import io.rqlite.client.L4Options;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.client.L4Options.*;
import static java.lang.String.format;

public class L4Driver implements Driver {

  private static final String JDBC_URL_PREFIX = "jdbc:rqlite:";
  private static final Logger log = Logger.getLogger(L4Driver.class.getName());
  private static final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();
  private static final int MaxCachedHttpClients = 64;

  static {
    try {
      DriverManager.registerDriver(new L4Driver());
    } catch (SQLException e) {
      throw new RuntimeException("Failed to register L4Driver", e);
    }
  }

  @Override public boolean acceptsURL(String url) {
    if (url == null) {
      return false;
    }
    return url.startsWith(JDBC_URL_PREFIX);
  }

  public Map<String, String> getQueryParams(String url) throws SQLException {
    if (!acceptsURL(url)) {
      throw badParam(format("Invalid rqlite JDBC URL: %s", url));
    }
    try {
      var rqliteUrl = url.substring(JDBC_URL_PREFIX.length());
      var urlParts = rqliteUrl.split("\\?", 2);
      var queryParams = new HashMap<String, String>();
      queryParams.put(kBaseUrl, urlParts[0]);
      if (urlParts.length > 1) {
        var params = urlParts[1].split("&");
        for (var param : params) {
          var keyValue = param.split("=", 2);
          if (keyValue.length == 2) {
            queryParams.put(keyValue[0].toLowerCase(), keyValue[1]);
          }
        }
      }
      return queryParams;
    } catch (Exception e) {
      throw badParam(e);
    }
  }

  public HttpClient createHttpClient() throws SQLException {
    return createHttpClient(new L4Options());
  }

  /**
   * Returns a shared {@link HttpClient} for the given target, creating one on first use.
   * Sharing avoids spawning a new selector thread and connection pool per JDBC connection,
   * which matters under connection pooling. The client is keyed by target and TLS identity.
   */
  public HttpClient createHttpClient(L4Options options) throws SQLException {
    var key = options.baseUrl + "|" + options.insecure + "|" + options.cacert
      + "|" + options.clientCert + "|" + options.clientKey;
    var existing = httpClients.get(key);
    if (existing != null) {
      return existing;
    }
    var built = buildHttpClient(options);
    // Bound the cache: shared clients are keyed by target/TLS identity and are normally
    // few, but guard against unbounded growth from many distinct targets.
    if (httpClients.size() >= MaxCachedHttpClients) {
      httpClients.clear();
    }
    var prev = httpClients.putIfAbsent(key, built);
    return prev != null ? prev : built;
  }

  /**
   * Evicts all cached shared {@link HttpClient} instances. Existing connections keep working
   * (they hold their own references); subsequent connections rebuild as needed.
   */
  public static void clearHttpClients() {
    httpClients.clear();
  }

  private HttpClient buildHttpClient(L4Options options) throws SQLException {
    try {
      var isHttps = options.baseUrl.toLowerCase().startsWith("https://");
      var cacert = options.cacert;
      var clientCert = options.clientCert;
      var clientKey = options.clientKey;
      if (!isHttps) {
        return L4Http.defaultHttpClient(options.timeoutSec).build();
      } else if (clientCert != null && !clientCert.isEmpty() && clientKey != null && !clientKey.isEmpty()) {
        return L4Http.newMTlsClient(cacert, clientCert, clientKey, options.timeoutSec).build();
      } else if (options.insecure) {
        return L4Http.newTLSSClientInsecure(options.timeoutSec).build();
      } else if (cacert != null && !cacert.isEmpty()) {
        return L4Http.newTLSSClient(cacert, options.timeoutSec).build();
      } else {
        var builder = HttpClient.newBuilder().sslContext(SSLContext.getDefault());
        if (options.timeoutSec > 0) {
          builder.connectTimeout(Duration.ofSeconds(options.timeoutSec));
        }
        return builder.build();
      }
    } catch (Exception e) {
      throw badParam(e);
    }
  }

  public L4Client createL4Client(HttpClient httpClient) throws SQLException {
    return createL4Client(new L4Options(), httpClient);
  }

  public L4Client createL4Client(L4Options options, HttpClient httpClient) throws SQLException {
    try {
      var client = new L4Client(options.baseUrl, httpClient, options);
      if (options.user != null && options.password != null) {
        return client.withBasicAuth(options.user, options.password);
      }
      return client;
    } catch (Exception e) {
      throw new SQLException("Failed to create L4Client: " + e.getMessage(), e);
    }
  }

  private Properties mergeProperties(Properties info, Map<String, String> queryParams) {
    var merged = new Properties();
    queryParams.forEach(merged::setProperty);
    if (info != null) {
      merged.putAll(info);
    }
    return merged;
  }

  @Override public Connection connect(String url, Properties info) throws SQLException {
    if (!acceptsURL(url)) {
      return null;
    }
    try {
      var options = new L4Options(mergeProperties(info, getQueryParams(url)));
      var httpClient = createHttpClient(options);
      var client = createL4Client(options, httpClient);
      return new L4Conn(client);
    } catch (Exception e) {
      throw badState("Failed to establish connection", e);
    }
  }

  private static final class Prop {
    final String key;
    final String description;
    final Supplier<String> defaultValue;

    Prop(String key, String description, Supplier<String> defaultValue) {
      this.key = key;
      this.description = description;
      this.defaultValue = defaultValue;
    }
  }

  @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
    var mergedProps = mergeProperties(info, new HashMap<>());
    var d = new L4Options();
    var defs = new Prop[] {
      new Prop(kUser, "Username for rqlite authentication", () -> null),
      new Prop(kPassword, "Password for rqlite authentication", () -> null),
      new Prop(kTimeoutSec, "Timeout in seconds", () -> String.valueOf(d.timeoutSec)),
      new Prop(kQueue, "Enable queue mode", () -> String.valueOf(d.queue)),
      new Prop(kWait, "Enable wait mode", () -> String.valueOf(d.wait)),
      new Prop(kLevel, "Consistency level (none, weak, strong, linearizable, auto)", () -> d.level.toString()),
      new Prop(kLinearizableTimeoutSec, "Linearizable timeout in seconds", () -> String.valueOf(d.linearizableTimeoutSec)),
      new Prop(kFreshnessSec, "Freshness in seconds", () -> String.valueOf(d.freshnessSec)),
      new Prop(kFreshnessStrict, "Enable strict freshness", () -> String.valueOf(d.freshnessStrict)),
      new Prop(kCaCert, "Path to CA certificate for HTTPS connections", () -> null),
      new Prop(kRetries, "Number of request-forwarding retries", () -> String.valueOf(d.retries)),
      new Prop(kRedirect, "Follow rqlite leader redirects (HTTP 301)", () -> String.valueOf(d.redirect)),
      new Prop(kClientCert, "Path to PEM client certificate for mTLS", () -> null),
      new Prop(kClientKey, "Path to unencrypted PKCS#8 PEM client key for mTLS", () -> null)
    };
    var props = new DriverPropertyInfo[defs.length];
    for (int i = 0; i < defs.length; i++) {
      var def = defs[i];
      props[i] = new DriverPropertyInfo(def.key, mergedProps.getProperty(def.key, def.defaultValue.get()));
      props[i].description = def.description;
      props[i].required = false;
    }
    return props;
  }

  @Override public int getMajorVersion() {
    return driverVersionMajor();
  }

  @Override public int getMinorVersion() {
    return driverVersionMinor();
  }

  @Override public boolean jdbcCompliant() {
    return false;
  }

  @Override public Logger getParentLogger() {
    return log;
  }

}