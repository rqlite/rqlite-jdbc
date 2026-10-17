package io.rqlite.client;

import java.net.http.HttpResponse;

import static java.lang.String.format;

public class L4Err {

  public static IllegalStateException responseError(int status, String body) {
    return new IllegalStateException(format(
      "HTTP response error: [%d]%s", status,
      body != null ? format(" - %s", body) : ""
    ));
  }

  public static IllegalStateException requestError(String message) {
    return new IllegalStateException(message);
  }

  public static IllegalStateException missingResult() {
    return new IllegalStateException("missing result");
  }

  public static IllegalStateException tooManyRedirects(int hop) {
    return new IllegalStateException(format("Too many redirects [%d]", hop));
  }

  public static IllegalStateException httpTransportError(String method, int status, String url, Exception cause) {
    return new IllegalStateException(format("HTTP %s error: (%d) [%s]", method, status, url), cause);
  }

  public static IllegalArgumentException invalidTimeout(long value) {
    return new IllegalArgumentException(format("Invalid timeout [%d]", value));
  }

  public static IllegalStateException mixedStatementParams() {
    return new IllegalStateException("Cannot mix positional and named parameters in the same statement");
  }

  public static IllegalArgumentException negativeParamIndex(int paramIndex) {
    return new IllegalArgumentException("Parameter index must be non-negative: " + paramIndex);
  }

  public static IllegalStateException emptyStatement() {
    return new IllegalStateException("SQL statement cannot be null or empty");
  }

  public static IllegalArgumentException unknownColumn(String column) {
    return new IllegalArgumentException("Unknown column: " + column);
  }

  public static IllegalArgumentException invalidErrorsFormat(String value) {
    return new IllegalArgumentException(format("Invalid errors format: [%s] (expected v1 or v2)", value));
  }

  public static IllegalStateException optionsUpdateFailed(Exception cause) {
    return new IllegalStateException(cause);
  }

  public static IllegalArgumentException noClientCertificates(String path) {
    return new IllegalArgumentException("No certificates found in: " + path);
  }

  public static IllegalArgumentException invalidClientKey(String path) {
    return new IllegalArgumentException(
      "Client key must be an unencrypted PKCS#8 PEM (-----BEGIN PRIVATE KEY-----): " + path
    );
  }

  public static IllegalArgumentException unsupportedKeyAlgorithm(String path) {
    return new IllegalArgumentException("Unsupported client private key algorithm in: " + path);
  }

  public static HttpResponse<String> checkResponse(HttpResponse<String> res) {
    if (res.statusCode() != 200) {
      throw responseError(res.statusCode(), res.body());
    }
    return res;
  }

  public static L4Result checkResult(L4Result result) {
    if (result == null) {
      throw missingResult();
    }
    if (result.error != null) {
      throw new L4StatementException(result.error);
    }
    return result;
  }

}
