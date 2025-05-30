package io.rqlite.client;

import java.io.Closeable;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import io.rqlite.jdbc.L4Log;
import io.rqlite.json.Json;
import io.rqlite.json.JsonValue;

import static io.rqlite.client.L4Err.*;
import static java.lang.String.format;

public class L4Client implements Closeable {

  private HttpClient httpClient;
  private final String baseUrl;
  private final String executeURL;
  private final String queryURL;
  private final String statusURL;
  private final String nodesURL;
  private final String readyURL;

  public  String basicAuthUser = "";
  private String basicAuthPass = "";

  public L4Client(String baseURL, HttpClient client) {
    this.baseUrl = Objects.requireNonNull(baseURL);
    this.executeURL = baseURL + "/db/execute";
    this.queryURL = baseURL + "/db/query";
    this.statusURL = baseURL + "/status";
    this.nodesURL = baseURL + "/nodes";
    this.readyURL = baseURL + "/readyz";
    this.httpClient = client != null
      ? client
      : L4Http.defaultHttpClient(L4Options.timeoutSec).build();
  }

  private HttpResponse<String> doPostRequest(String url, String body) {
    try {
      L4Log.l4Trace("POST {}", body);
      var builder = HttpRequest.newBuilder().uri(URI.create(url));
      if (L4Options.timeoutSec > 0) {
        builder.timeout(Duration.ofSeconds(L4Options.timeoutSec));
      }
      builder.method("POST", HttpRequest.BodyPublishers.ofString(body));
      builder.header("Content-Type", "application/json");
      addBasicAuth(builder);
      var req = builder.build();
      return checkResponse(httpClient.send(req, HttpResponse.BodyHandlers.ofString()));
    } catch (Exception e) {
      throw new IllegalStateException(format("HTTP POST error: [%s]", url), e);
    }
  }

  private HttpResponse<String> doJSONPostRequest(String url, String body) {
    return doPostRequest(url, body);
  }

  private HttpResponse<String> doGetRequest(String url) {
    try {
      var builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
      addBasicAuth(builder);
      if (L4Options.timeoutSec > 0) {
        builder.timeout(Duration.ofSeconds(L4Options.timeoutSec));
      }
      var req = builder.build();
      return checkResponse(httpClient.send(req, HttpResponse.BodyHandlers.ofString()));
    } catch (Exception e) {
      throw new IllegalStateException(format("HTTP GET error: [%s]", url), e);
    }
  }

  private void addBasicAuth(HttpRequest.Builder builder) {
    if (!basicAuthUser.isEmpty() || !basicAuthPass.isEmpty()) {
      String auth = basicAuthUser + ":" + basicAuthPass;
      String encoded = Base64.getEncoder().encodeToString(auth.getBytes());
      builder.header("Authorization", "Basic " + encoded);
    }
  }

  public L4Client withBasicAuth(String username, String password) {
    this.basicAuthUser = username;
    this.basicAuthPass = password;
    return this;
  }

  public L4Response execute(boolean transaction, L4Statement ... statements) {
    var body = L4Statement.toArray(statements).toString();
    var queryParams = L4Options.queryParams(transaction);
    var resp = doJSONPostRequest(executeURL + queryParams, body);
    var rb = resp.body();
    var node = Json.parse(rb).asObject();
    return new L4Response(resp.statusCode(), node);
  }

  public L4Response executeSingle(String statement, Object... args) {
    var res = execute(true, new L4Statement().sql(statement).withPositionalParams(args));
    checkResult(res.first());
    return res;
  }

  public L4Response query(L4Statement ... statements) {
    var body = L4Statement.toArray(statements).toString();
    var queryParams = L4Options.queryParams(false);
    var resp = doJSONPostRequest(queryURL + queryParams, body);
    var rb = resp.body();
    var node = Json.parse(rb).asObject();
    return new L4Response(resp.statusCode(), node);
  }

  public L4Response querySingle(String statement, Object... args) {
    var res = query(new L4Statement().sql(statement).withPositionalParams(args));
    checkResult(res.first());
    return res;
  }

  public JsonValue status() {
    var resp = doGetRequest(statusURL);
    return Json.parse(resp.body());
  }

  public JsonValue nodes() {
    var resp = doGetRequest(nodesURL);
    return Json.parse(resp.body());
  }

  public String ready() {
    var resp = doGetRequest(readyURL);
    return resp.body();
  }

  public void withTxTimeoutSec(long txTimeoutSec) {
    if (txTimeoutSec < 0) {
      throw new IllegalArgumentException(format("Invalid timeout [%d]", txTimeoutSec));
    }
    L4Options.timeoutSec = txTimeoutSec == 0 ? -1 : txTimeoutSec;
  }

  public long getTxTimeoutSec() {
    return L4Options.timeoutSec;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  @Override public void close() {
    // only Java 21+ supports explicitly closing the http client... sigh...
    this.httpClient = null;
  }

}
