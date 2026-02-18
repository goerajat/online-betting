package com.betting.etrade.oauth;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling E*TRADE OAuth 1.0 authentication flow.
 */
public class OAuthService {

    private static final Logger log = LoggerFactory.getLogger(OAuthService.class);

    private final OAuthConfig config;
    private final OAuthSigner signer;
    private final OkHttpClient httpClient;
    private OAuthToken accessToken;

    public OAuthService(OAuthConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.signer = new OAuthSigner(config);
        this.httpClient = httpClient;
    }

    /**
     * Check if the service has valid access tokens.
     */
    public boolean isAuthenticated() {
        return accessToken != null;
    }

    /**
     * Get the current access token.
     */
    public OAuthToken getAccessToken() {
        return accessToken;
    }

    /**
     * Set access token directly (useful for token persistence).
     */
    public void setAccessToken(OAuthToken token) {
        this.accessToken = token;
    }

    /**
     * Step 1: Get a request token.
     */
    public OAuthToken getRequestToken() throws IOException {
        String url = config.getRequestTokenUrl();
        // E*TRADE requires oauth_callback parameter; "oob" means out-of-band (manual code entry)
        String authHeader = signer.generateAuthorizationHeader("GET", url, null, null, "oob", null);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .get()
                .build();

        log.debug("Requesting token from: {}", url);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get request token: " + response.code() +
                        " - " + response.body().string());
            }

            String body = response.body().string();
            return parseTokenResponse(body);
        }
    }

    /**
     * Step 2: Get authorization URL for user to visit.
     */
    public String getAuthorizationUrl(OAuthToken requestToken) {
        return config.getAuthorizationUrl(requestToken.getToken());
    }

    /**
     * Step 3: Exchange request token + verifier for access token.
     */
    public OAuthToken getAccessToken(OAuthToken requestToken, String verifier) throws IOException {
        String url = config.getAccessTokenUrl();
        String authHeader = signer.generateAuthorizationHeader("GET", url, requestToken, verifier, null);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", authHeader)
                .get()
                .build();

        log.debug("Exchanging for access token at: {}", url);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get access token: " + response.code() +
                        " - " + response.body().string());
            }

            String body = response.body().string();
            this.accessToken = parseTokenResponse(body);
            return this.accessToken;
        }
    }

    /**
     * Make an authenticated API request.
     */
    public Response executeRequest(String method, String url, Map<String, String> queryParams,
            String body) throws IOException {

        // Build URL with query parameters
        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
        if (queryParams != null) {
            queryParams.forEach(urlBuilder::addQueryParameter);
        }
        HttpUrl finalUrl = urlBuilder.build();

        // Generate OAuth header
        Map<String, String> allParams = new HashMap<>();
        if (queryParams != null) {
            allParams.putAll(queryParams);
        }

        String authHeader = signer.generateAuthorizationHeader(method, url, accessToken, null, allParams);

        Request.Builder requestBuilder = new Request.Builder()
                .url(finalUrl)
                .addHeader("Authorization", authHeader)
                .addHeader("Accept", "application/json");

        if ("POST".equalsIgnoreCase(method) && body != null) {
            requestBuilder.post(RequestBody.create(body, MediaType.parse("application/json")));
        } else if ("GET".equalsIgnoreCase(method)) {
            requestBuilder.get();
        }

        return httpClient.newCall(requestBuilder.build()).execute();
    }

    /**
     * Parse OAuth token response (form-encoded).
     */
    private OAuthToken parseTokenResponse(String response) {
        Map<String, String> params = new HashMap<>();
        for (String pair : response.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                // URL-decode the values as they come encoded in the response
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }

        String token = params.get("oauth_token");
        String tokenSecret = params.get("oauth_token_secret");

        if (token == null || tokenSecret == null) {
            throw new IllegalStateException("Invalid token response: " + response);
        }

        return new OAuthToken(token, tokenSecret);
    }

    public OAuthConfig getConfig() {
        return config;
    }

    public OAuthSigner getSigner() {
        return signer;
    }
}
