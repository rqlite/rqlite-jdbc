package io.rqlite.jdbc;

import io.rqlite.client.L4Client;
import io.rqlite.client.L4Http;
import io.rqlite.client.L4Options;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

import static io.rqlite.jdbc.L4Err.*;
import static io.rqlite.jdbc.L4Jdbc.*;
import static io.rqlite.client.L4Options.*;
import static java.lang.String.format;

public class L4Driver implements Driver {

  private static final String JDBC_URL_PREFIX = "jdbc:rqlite:";
  private static final Logger log = Logger.getLogger(L4Driver.class.getName());

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
    // New recommended prefix
    if (url.startsWith(JDBC_URL_PREFIX)) {
      return true;
    }
    // Old deprecated prefix, but only for rqlite-style (http) URLs
    // TODO remove in a couple of releases
    return url.startsWith("jdbc:sqlite:http");
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
    try {
      var isHttps = L4Options.baseUrl.toLowerCase().startsWith("https://");
      var cacert = L4Options.cacert;
      if (!isHttps) {
        return L4Http.defaultHttpClient(L4Options.timeoutSec).build();
      } else if (L4Options.insecure) {
        return L4Http.newTLSSClientInsecure(L4Options.timeoutSec).build();
      } else if (cacert != null && !cacert.isEmpty()) {
        return L4Http.newTLSSClient(cacert, L4Options.timeoutSec).build();
      } else {
        return HttpClient.newBuilder()
          .sslContext(SSLContext.getDefault())
          .connectTimeout(Duration.ofSeconds(L4Options.timeoutSec))
          .build();
      }
    } catch (Exception e) {
      throw badParam(e);
    }
  }

  public L4Client createL4Client(HttpClient httpClient) throws SQLException {
    try {
      var user = L4Options.user;
      var password = L4Options.password;
      var client = new L4Client(L4Options.baseUrl, httpClient);
      if (user != null && password != null) {
        return client.withBasicAuth(user, password);
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
      L4Options.update(mergeProperties(info, getQueryParams(url)));
      var httpClient = createHttpClient();
      var client = createL4Client(httpClient);
      return new L4Conn(client);
    } catch (Exception e) {
      throw badState("Failed to establish connection", e);
    }
  }

  @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
    var mergedProps = mergeProperties(info, new HashMap<>());
    var props = new DriverPropertyInfo[10];

    props[0] = new DriverPropertyInfo(kUser, mergedProps.getProperty(kUser));
    props[0].description = "Username for rqlite authentication";
    props[0].required = false;

    props[1] = new DriverPropertyInfo(kPassword, mergedProps.getProperty(kPassword));
    props[1].description = "Password for rqlite authentication";
    props[1].required = false;

    props[2] = new DriverPropertyInfo(kTimeoutSec, mergedProps.getProperty(kTimeoutSec, String.valueOf(L4Options.timeoutSec)));
    props[2].description = "Timeout in seconds";
    props[2].required = false;

    props[3] = new DriverPropertyInfo(kQueue, mergedProps.getProperty(kQueue, String.valueOf(L4Options.queue)));
    props[3].description = "Enable queue mode";
    props[3].required = false;

    props[4] = new DriverPropertyInfo(kWait, mergedProps.getProperty(kWait, String.valueOf(L4Options.wait)));
    props[4].description = "Enable wait mode";
    props[4].required = false;

    props[5] = new DriverPropertyInfo(kLevel, mergedProps.getProperty(kLevel, L4Options.level.toString()));
    props[5].description = "Consistency level (none, weak, linearizable)";
    props[5].required = false;

    props[6] = new DriverPropertyInfo(kLinearizableTimeoutSec, mergedProps.getProperty(kLinearizableTimeoutSec, String.valueOf(L4Options.linearizableTimeoutSec)));
    props[6].description = "Linearizable timeout in seconds";
    props[6].required = false;

    props[7] = new DriverPropertyInfo(kFreshnessSec, mergedProps.getProperty(kFreshnessSec, String.valueOf(L4Options.freshnessSec)));
    props[7].description = "Freshness in seconds";
    props[7].required = false;

    props[8] = new DriverPropertyInfo(kFreshnessStrict, mergedProps.getProperty(kFreshnessStrict, String.valueOf(L4Options.freshnessStrict)));
    props[8].description = "Enable strict freshness";
    props[8].required = false;

    props[9] = new DriverPropertyInfo(kCaCert, mergedProps.getProperty(kCaCert));
    props[9].description = "Path to CA certificate for HTTPS connections";
    props[9].required = false;

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