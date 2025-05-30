package io.rqlite.client;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.security.*;
import java.security.cert.*;
import java.time.Duration;

public class L4Http {

  public static HttpClient.Builder defaultHttpClient(long timeoutSec) {
    return HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(timeoutSec));
  }

  public static HttpClient.Builder newTLSSClientInsecure(long timeoutSec) throws Exception {
    var sslContext = SSLContext.getInstance("TLS");
    var trustAll = new TrustManager[]{
      new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      }
    };
    sslContext.init(null, trustAll, new SecureRandom());
    return HttpClient.newBuilder()
      .sslContext(sslContext)
      .connectTimeout(Duration.ofSeconds(timeoutSec));
  }

  public static HttpClient.Builder newTLSSClient(String caCertPath, long timeoutSec) throws Exception {
    var cf = CertificateFactory.getInstance("X.509");
    var caBytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(caCertPath));
    var bis = new ByteArrayInputStream(caBytes);
    var caCert = (X509Certificate) cf.generateCertificate(bis);

    var ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    ks.setCertificateEntry("caCert", caCert);

    var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ks);

    var sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
    return HttpClient.newBuilder()
      .sslContext(sslContext)
      .connectTimeout(Duration.ofSeconds(timeoutSec));
  }

}
