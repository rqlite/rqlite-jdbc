package io.rqlite.client;

import java.net.http.HttpResponse;

import static java.lang.String.format;

public class L4Err {

  public static HttpResponse<String> checkResponse(HttpResponse<String> res) {
    if (res.statusCode() != 200) {
      var body = res.body();
      throw new IllegalStateException(format(
        "HTTP response error: [%d]%s", res.statusCode(),
        body != null ? format(" - %s", body) : ""
      ));
    }
    return res;
  }

  public static L4Result checkResult(L4Result result) {
    if (result == null) {
      throw new IllegalStateException("missing result");
    }
    return result;
  }

}
