package io.rqlite;

import io.rqlite.client.L4Http;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.nio.file.Files;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class L4HttpTest {

  static {
    it("Builds default and insecure TLS clients", () -> {
      assertNotNull(L4Http.defaultHttpClient(5).build());
      assertNotNull(L4Http.newTLSSClientInsecure(5).build());
    });

    it("Rejects a CA certificate path that does not exist", () -> {
      try {
        L4Http.newTLSSClient("/nonexistent/ca.crt", 5);
        fail("Expected exception for missing CA certificate");
      } catch (Exception e) {
        assertNotNull(e.getMessage());
      }
    });

    it("Rejects mTLS client certificates with missing files", () -> {
      try {
        L4Http.newMTlsClient(null, "/nonexistent/client.crt", "/nonexistent/client.key", 5);
        fail("Expected exception for missing client certificate");
      } catch (Exception e) {
        assertNotNull(e.getMessage());
      }
    });

    it("Rejects mTLS material that is not valid PEM", () -> {
      var dir = Files.createTempDirectory("l4http");
      var cert = dir.resolve("client.crt");
      var key = dir.resolve("client.key");
      Files.writeString(cert, "not a certificate");
      Files.writeString(key, "not a private key");
      try {
        L4Http.newMTlsClient(null, cert.toString(), key.toString(), 5);
        fail("Expected exception for invalid mTLS material");
      } catch (Exception e) {
        assertNotNull(e.getMessage());
      }
    });

    it("Rejects mTLS keys that are not PKCS#8 PEM", () -> {
      var dir = Files.createTempDirectory("l4http");
      var cert = dir.resolve("client.crt");
      var key = dir.resolve("client.key");
      Files.writeString(cert, "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----");
      Files.writeString(key, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----");
      try {
        L4Http.newMTlsClient(null, cert.toString(), key.toString(), 5);
        fail("Expected exception for non-PKCS#8 private key");
      } catch (Exception e) {
        assertNotNull(e.getMessage());
      }
    });
  }

}
