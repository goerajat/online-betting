package com.kalshi.client.auth;

import com.kalshi.client.exception.AuthenticationException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class KalshiAuthenticatorTest {

    @Test
    void testGenerateHeaders() throws Exception {
        // Generate a test RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String apiKeyId = "test-api-key";
        KalshiAuthenticator authenticator = new KalshiAuthenticator(apiKeyId, keyPair.getPrivate());

        KalshiAuthenticator.AuthHeaders headers = authenticator.generateHeaders("GET", "/trade-api/v2/markets");

        assertEquals(apiKeyId, headers.getAccessKey());
        assertNotNull(headers.getTimestamp());
        assertNotNull(headers.getSignature());

        // Verify timestamp is recent (within 5 seconds)
        long timestamp = Long.parseLong(headers.getTimestamp());
        long now = System.currentTimeMillis();
        assertTrue(Math.abs(now - timestamp) < 5000);
    }

    @Test
    void testSignatureFormat() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        KalshiAuthenticator authenticator = new KalshiAuthenticator("test-key", keyPair.getPrivate());

        String signature = authenticator.sign(1234567890123L, "GET", "/trade-api/v2/markets");

        // Verify it's valid base64
        assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
    }

    @Test
    void testFromPem() throws Exception {
        // Generate a key pair and convert to PEM format
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        String pemKey = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        KalshiAuthenticator authenticator = KalshiAuthenticator.fromPem("test-key", pemKey);

        assertNotNull(authenticator);
        assertEquals("test-key", authenticator.getApiKeyId());
    }

    @Test
    void testFromPemInvalidKey() {
        assertThrows(AuthenticationException.class, () ->
            KalshiAuthenticator.fromPem("test-key", "invalid-pem-content")
        );
    }

    @Test
    void testDifferentHttpMethods() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        KalshiAuthenticator authenticator = new KalshiAuthenticator("test-key", keyPair.getPrivate());
        long timestamp = System.currentTimeMillis();
        String path = "/trade-api/v2/portfolio/orders";

        String getSignature = authenticator.sign(timestamp, "GET", path);
        String postSignature = authenticator.sign(timestamp, "POST", path);
        String deleteSignature = authenticator.sign(timestamp, "DELETE", path);

        // Different HTTP methods should produce different signatures
        assertNotEquals(getSignature, postSignature);
        assertNotEquals(getSignature, deleteSignature);
        assertNotEquals(postSignature, deleteSignature);
    }

    @Test
    void testSameInputProducesDifferentSignatures() throws Exception {
        // Due to PSS randomness, same input should produce different signatures
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        KalshiAuthenticator authenticator = new KalshiAuthenticator("test-key", keyPair.getPrivate());
        long timestamp = System.currentTimeMillis();
        String path = "/trade-api/v2/markets";

        String signature1 = authenticator.sign(timestamp, "GET", path);
        String signature2 = authenticator.sign(timestamp, "GET", path);

        // PSS signatures are randomized, so they should be different
        // (This tests that we're actually using PSS correctly)
        assertNotEquals(signature1, signature2);
    }
}
