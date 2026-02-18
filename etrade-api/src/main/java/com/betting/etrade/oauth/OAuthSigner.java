package com.betting.etrade.oauth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * OAuth 1.0 signature generator using HMAC-SHA1.
 */
public class OAuthSigner {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthConfig config;

    public OAuthSigner(OAuthConfig config) {
        this.config = config;
    }

    /**
     * Generate OAuth authorization header for a request.
     */
    public String generateAuthorizationHeader(String method, String url,
            OAuthToken token, String verifier, Map<String, String> additionalParams) {
        return generateAuthorizationHeader(method, url, token, verifier, null, additionalParams);
    }

    /**
     * Generate OAuth authorization header for a request with callback.
     */
    public String generateAuthorizationHeader(String method, String url,
            OAuthToken token, String verifier, String callback, Map<String, String> additionalParams) {

        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = generateNonce();

        // Build OAuth parameters
        Map<String, String> oauthParams = new TreeMap<>();
        oauthParams.put("oauth_consumer_key", config.getConsumerKey());
        oauthParams.put("oauth_signature_method", "HMAC-SHA1");
        oauthParams.put("oauth_timestamp", String.valueOf(timestamp));
        oauthParams.put("oauth_nonce", nonce);
        oauthParams.put("oauth_version", "1.0");

        if (callback != null) {
            oauthParams.put("oauth_callback", callback);
        }

        if (token != null) {
            oauthParams.put("oauth_token", token.getToken());
        }

        if (verifier != null) {
            oauthParams.put("oauth_verifier", verifier);
        }

        // Combine with additional parameters for signature
        Map<String, String> allParams = new TreeMap<>(oauthParams);
        if (additionalParams != null) {
            allParams.putAll(additionalParams);
        }

        // Generate signature
        String signature = generateSignature(method, url, allParams,
                token != null ? token.getTokenSecret() : "");
        oauthParams.put("oauth_signature", signature);

        // Build authorization header
        return buildAuthorizationHeader(oauthParams);
    }

    /**
     * Generate the OAuth signature.
     */
    private String generateSignature(String method, String url,
            Map<String, String> params, String tokenSecret) {

        // Build parameter string (sorted alphabetically)
        String paramString = params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));

        // Build signature base string
        String baseString = method.toUpperCase() + "&" +
                encode(normalizeUrl(url)) + "&" +
                encode(paramString);

        // Build signing key
        String signingKey = encode(config.getConsumerSecret()) + "&" +
                (tokenSecret != null ? encode(tokenSecret) : "");

        // Generate HMAC-SHA1 signature
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            SecretKeySpec keySpec = new SecretKeySpec(
                    signingKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA1);
            mac.init(keySpec);
            byte[] result = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    /**
     * Build the Authorization header value.
     */
    private String buildAuthorizationHeader(Map<String, String> oauthParams) {
        String params = oauthParams.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=\"" + encode(e.getValue()) + "\"")
                .collect(Collectors.joining(", "));
        return "OAuth " + params;
    }

    /**
     * Generate a random nonce.
     */
    private String generateNonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes)
                .replaceAll("[^a-zA-Z0-9]", "");
    }

    /**
     * URL encode a string according to OAuth spec.
     */
    private String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    /**
     * Normalize URL by removing query string and default ports.
     */
    private String normalizeUrl(String url) {
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            url = url.substring(0, queryIndex);
        }
        return url.replaceFirst(":80/", "/").replaceFirst(":443/", "/");
    }
}
