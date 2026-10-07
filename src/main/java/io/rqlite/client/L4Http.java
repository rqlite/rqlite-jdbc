package io.rqlite.client;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;

public class L4Http {

  public static HttpClient.Builder defaultHttpClient(long timeoutSec) {
    var builder = HttpClient.newBuilder();
    if (timeoutSec > 0) {
      builder.connectTimeout(Duration.ofSeconds(timeoutSec));
    }
    return builder;
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
    var builder = HttpClient.newBuilder().sslContext(sslContext);
    if (timeoutSec > 0) {
      builder.connectTimeout(Duration.ofSeconds(timeoutSec));
    }
    return builder;
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
    var builder = HttpClient.newBuilder().sslContext(sslContext);
    if (timeoutSec > 0) {
      builder.connectTimeout(Duration.ofSeconds(timeoutSec));
    }
    return builder;
  }

  /**
   * Builds an HttpClient that presents a client certificate (mTLS). The client certificate
   * and key are expected as PEM files; the private key must be an unencrypted PKCS#8 key
   * ({@code -----BEGIN PRIVATE KEY-----}). The CA certificate is optional; when omitted the
   * JVM default trust store is used.
   */
  public static HttpClient.Builder newMTlsClient(String caCertPath, String clientCertPath,
                                                 String clientKeyPath, long timeoutSec) throws Exception {
    var cf = CertificateFactory.getInstance("X.509");

    var certList = new ArrayList<java.security.cert.Certificate>();
    try (var in = Files.newInputStream(Paths.get(clientCertPath))) {
      for (var c : cf.generateCertificates(in)) {
        certList.add(c);
      }
    }
    if (certList.isEmpty()) {
      throw new IllegalArgumentException("No certificates found in: " + clientCertPath);
    }
    var certs = certList.toArray(new java.security.cert.Certificate[0]);

    var privateKey = readPkcs8PrivateKey(clientKeyPath);

    var ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(null, null);
    ks.setKeyEntry("client", privateKey, new char[0], certs);

    var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, new char[0]);

    TrustManager[] trustManagers = null;
    if (caCertPath != null && !caCertPath.isEmpty()) {
      var caBytes = Files.readAllBytes(Paths.get(caCertPath));
      var caCert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(caBytes));
      var ts = KeyStore.getInstance(KeyStore.getDefaultType());
      ts.load(null, null);
      ts.setCertificateEntry("caCert", caCert);
      var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ts);
      trustManagers = tmf.getTrustManagers();
    }

    var sslContext = SSLContext.getInstance("TLS");
    sslContext.init(kmf.getKeyManagers(), trustManagers, new SecureRandom());
    var builder = HttpClient.newBuilder().sslContext(sslContext);
    if (timeoutSec > 0) {
      builder.connectTimeout(Duration.ofSeconds(timeoutSec));
    }
    return builder;
  }

  private static PrivateKey readPkcs8PrivateKey(String path) throws Exception {
    var pem = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.US_ASCII);
    var header = "-----BEGIN PRIVATE KEY-----";
    var footer = "-----END PRIVATE KEY-----";
    var start = pem.indexOf(header);
    var end = pem.indexOf(footer);
    if (start < 0 || end < 0) {
      throw new IllegalArgumentException(
        "Client key must be an unencrypted PKCS#8 PEM (" + header + "): " + path
      );
    }
    var base64 = pem.substring(start + header.length(), end).replaceAll("\\s", "");
    var der = Base64.getDecoder().decode(base64);
    var spec = new PKCS8EncodedKeySpec(der);
    for (var algorithm : new String[] { "RSA", "EC", "Ed25519" }) {
      try {
        return KeyFactory.getInstance(algorithm).generatePrivate(spec);
      } catch (Exception ignore) {
        // try the next algorithm
      }
    }
    throw new IllegalArgumentException("Unsupported client private key algorithm in: " + path);
  }

}
