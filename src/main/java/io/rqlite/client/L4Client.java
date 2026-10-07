package io.rqlite.client;

import java.io.Closeable;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import io.rqlite.jdbc.L4Log;
import io.rqlite.json.*;

import static io.rqlite.client.L4Response.*;
import static io.rqlite.client.L4Err.*;
import static java.lang.String.format;

public class L4Client implements Closeable {

  private HttpClient   httpClient;
  private final String baseUrl;
  private final String executeURL;
  private final String queryURL;
  private final String requestURL;
  private final String statusURL;
  private final String nodesURL;
  private final String readyURL;
  private final L4Options options;

  public  String basicAuthUser = "";
  private String basicAuthPass = "";
  private List<L4Response> buffer;

  public L4Client(String baseURL, HttpClient client) {
    this(baseURL, client, new L4Options());
  }

  public L4Client(String baseURL, HttpClient client, L4Options options) {
    this.baseUrl = Objects.requireNonNull(baseURL);
    this.options = Objects.requireNonNull(options);
    this.executeURL = baseURL + "/db/execute";
    this.queryURL = baseURL + "/db/query";
    this.requestURL = baseURL + "/db/request";
    this.statusURL = baseURL + "/status";
    this.nodesURL = baseURL + "/nodes";
    this.readyURL = baseURL + "/readyz";
    this.httpClient = client != null
      ? client
      : L4Http.defaultHttpClient(options.timeoutSec).build();
  }

  private static final int MaxRedirects = 3;

  /**
   * Returns the redirect target if the response is a redirect that should be followed,
   * or null when the response should be handled as-is.
   */
  private String redirectTarget(HttpResponse<String> res, int hop) {
    if (!options.redirect) {
      return null;
    }
    var code = res.statusCode();
    if (code != 301 && code != 302 && code != 307 && code != 308) {
      return null;
    }
    if (hop >= MaxRedirects) {
      throw new IllegalStateException(format("Too many redirects [%d]", hop));
    }
    var loc = res.headers().firstValue("Location").orElse(null);
    return loc == null || loc.isEmpty() ? null : loc;
  }

  private HttpResponse<String> doPostRequest(String url, String body) {
    var currentUrl = url;
    var statusCode = -1;
    try {
      for (int hop = 0; ; hop++) {
        L4Log.trace("{} - POST {}", this, body);
        var builder = HttpRequest.newBuilder().uri(URI.create(currentUrl));
        if (options.timeoutSec > 0) {
          builder.timeout(Duration.ofSeconds(options.timeoutSec));
        }
        builder.method("POST", HttpRequest.BodyPublishers.ofString(body));
        builder.header("Content-Type", "application/json");
        addBasicAuth(builder);
        var res = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        statusCode = res.statusCode();
        var next = redirectTarget(res, hop);
        if (next == null) {
          return checkResponse(res);
        }
        currentUrl = next;
      }
    } catch (Exception e) {
      throw new IllegalStateException(format("HTTP POST error: (%d) [%s]", statusCode, currentUrl), e);
    }
  }

  private HttpResponse<String> doJSONPostRequest(String url, String body) {
    return doPostRequest(url, body);
  }

  private HttpResponse<String> doGetRequest(String url) {
    var currentUrl = url;
    var statusCode = -1;
    try {
      for (int hop = 0; ; hop++) {
        var builder = HttpRequest.newBuilder().uri(URI.create(currentUrl)).GET();
        addBasicAuth(builder);
        if (options.timeoutSec > 0) {
          builder.timeout(Duration.ofSeconds(options.timeoutSec));
        }
        var res = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        statusCode = res.statusCode();
        var next = redirectTarget(res, hop);
        if (next == null) {
          return checkResponse(res);
        }
        currentUrl = next;
      }
    } catch (Exception e) {
      throw new IllegalStateException(format("HTTP GET error: (%d) [%s]", statusCode, currentUrl), e);
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

  public boolean isBuffering() {
    return this.buffer != null;
  }

  public void startBuffer() {
    if (buffer == null) {
      buffer = new ArrayList<>();
    }
  }

  private L4Response toResponse(HttpResponse<String> resp) {
    var node = Json.parse(resp.body()).asObject();
    var r = response(resp.statusCode(), node);
    if (r.error != null) {
      throw new IllegalStateException(r.error);
    }
    return r;
  }

  private L4Response doExecute(boolean transaction, L4Statement ... statements) {
    var queryParams = options.queryParams(transaction);
    var url = executeURL + queryParams;
    var body = L4Statement.toArray(statements).toString();
    var resp = doJSONPostRequest(url, body);
    return toResponse(resp);
  }

  public void stopBuffer(boolean commit, Consumer<L4Response> responseFn) {
    if (commit && buffer != null && !buffer.isEmpty()) {
      var statements = buffer.stream()
        .flatMap(res -> Arrays.stream(res.statements))
        .toArray(L4Statement[]::new);
      buffer.clear();
      responseFn.accept(doExecute(true, statements));
    }
    buffer = null;
  }

  public L4Response execute(boolean transaction, L4Statement ... statements) {
    if (isBuffering()) {
      L4Log.trace("{} - defer: {}", this, Arrays.toString(statements));
      var res = deferred(statements);
      res.results = new ArrayList<>();
      res.results.add(new L4Result(new JsonObject()));
      this.buffer.add(res);
      return res;
    }
    return doExecute(transaction, statements);
  }

  public L4Response executeSingle(String statement, Object... args) {
    var res = execute(true, new L4Statement().sql(statement).withPositionalParams(args));
    checkResult(res.first());
    return res;
  }

  public L4Response query(L4Statement ... statements) {
    var body = L4Statement.toArray(statements).toString();
    var queryParams = options.queryParams(false);
    var resp = doJSONPostRequest(queryURL + queryParams, body);
    return toResponse(resp);
  }

  /**
   * Sends statements to rqlite's Unified Endpoint, which accepts both read and write
   * statements and classifies each one server-side. Use this when the statement type
   * is not known ahead of time.
   */
  public L4Response request(L4Statement ... statements) {
    var body = L4Statement.toArray(statements).toString();
    var queryParams = options.queryParams(false);
    var resp = doJSONPostRequest(requestURL + queryParams, body);
    return toResponse(resp);
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

  /**
   * Returns the rqlite build version reported by the server (without a leading 'v'),
   * or null if it cannot be determined.
   */
  public String rqliteVersion() {
    var status = status();
    if (status == null || !status.isObject()) {
      return null;
    }
    var build = status.asObject().get("build");
    if (build == null || !build.isObject()) {
      return null;
    }
    var version = build.asObject().get("version");
    if (version == null || !version.isString()) {
      return null;
    }
    var v = version.asString();
    return v.startsWith("v") ? v.substring(1) : v;
  }

  public JsonValue nodes() {
    var resp = doGetRequest(nodesURL);
    return Json.parse(resp.body());
  }

  public String ready() {
    var resp = doGetRequest(readyURL);
    return resp.body();
  }

  public void withQueryTimeoutSec(long queryTimeoutSec) {
    if (queryTimeoutSec < 0) {
      throw new IllegalArgumentException(format("Invalid timeout [%d]", queryTimeoutSec));
    }
    this.options.dbTimeoutSec = queryTimeoutSec;
  }

  public long getQueryTimeoutSec() {
    return this.options.dbTimeoutSec;
  }

  public void withNetworkTimeoutSec(long networkTimeoutSec) {
    if (networkTimeoutSec < 0) {
      throw new IllegalArgumentException(format("Invalid timeout [%d]", networkTimeoutSec));
    }
    this.options.timeoutSec = networkTimeoutSec;
  }

  public long getNetworkTimeoutSec() {
    return this.options.timeoutSec;
  }

  /** @deprecated use {@link #withNetworkTimeoutSec(long)} */
  @Deprecated
  public void withTxTimeoutSec(long txTimeoutSec) {
    withNetworkTimeoutSec(txTimeoutSec);
  }

  /** @deprecated use {@link #getNetworkTimeoutSec()} */
  @Deprecated
  public long getTxTimeoutSec() {
    return getNetworkTimeoutSec();
  }

  public L4Options getOptions() {
    return options;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  @Override public void close() {
    // only Java 21+ supports explicitly closing the http client... sigh...
    this.httpClient = null;
  }

  @Override public String toString() {
    return String.format("l4c [%08x, %03d]", this.hashCode(), buffer == null ? -1 : buffer.size());
  }

}
