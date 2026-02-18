package com.kalshi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.exception.AuthenticationException;
import com.kalshi.client.exception.KalshiApiException;
import com.kalshi.client.exception.OrderException;
import com.kalshi.client.exception.RateLimitException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Main HTTP client for Kalshi API.
 * Handles authentication, request signing, and response parsing.
 */
public class KalshiClient {

    private static final Logger logger = LoggerFactory.getLogger(KalshiClient.class);

    public static final String DEFAULT_BASE_URL = "https://api.elections.kalshi.com/trade-api/v2";
    public static final String DEMO_BASE_URL = "https://demo-api.kalshi.co/trade-api/v2";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final String baseUrl;
    private final KalshiAuthenticator authenticator;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private KalshiClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.authenticator = builder.authenticator;
        this.httpClient = builder.httpClient;
        this.objectMapper = createObjectMapper();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Execute a GET request.
     */
    public <T> T get(String path, Class<T> responseType) {
        return executeRequest("GET", path, null, responseType);
    }

    /**
     * Execute a GET request and parse the response using a specific JSON path.
     */
    public <T> T get(String path, String jsonPath, Class<T> responseType) {
        String response = executeRequest("GET", path, null, String.class);
        return parseJsonPath(response, jsonPath, responseType);
    }

    /**
     * Execute a POST request.
     */
    public <T> T post(String path, Object body, Class<T> responseType) {
        return executeRequest("POST", path, body, responseType);
    }

    /**
     * Execute a POST request and parse the response using a specific JSON path.
     */
    public <T> T post(String path, Object body, String jsonPath, Class<T> responseType) {
        String response = executeRequest("POST", path, body, String.class);
        return parseJsonPath(response, jsonPath, responseType);
    }

    /**
     * Execute a DELETE request.
     */
    public <T> T delete(String path, Class<T> responseType) {
        return executeRequest("DELETE", path, null, responseType);
    }

    /**
     * Execute a DELETE request with a body.
     */
    public <T> T delete(String path, Object body, Class<T> responseType) {
        return executeRequest("DELETE", path, body, responseType);
    }

    /**
     * Execute an HTTP request with optional authentication.
     */
    private <T> T executeRequest(String method, String path, Object body, Class<T> responseType) {
        // Build the request
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + path)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

        // Add authentication headers if authenticator is available
        if (authenticator != null) {
            String pathForSigning = path.contains("?") ? path.substring(0, path.indexOf("?")) : path;
            String fullPath = "/trade-api/v2" + pathForSigning;
            KalshiAuthenticator.AuthHeaders authHeaders = authenticator.generateHeaders(method, fullPath);
            requestBuilder.header("KALSHI-ACCESS-KEY", authHeaders.getAccessKey());
            requestBuilder.header("KALSHI-ACCESS-TIMESTAMP", authHeaders.getTimestamp());
            requestBuilder.header("KALSHI-ACCESS-SIGNATURE", authHeaders.getSignature());
        }

        // Set the request body
        RequestBody requestBody = null;
        if (body != null) {
            try {
                String json = objectMapper.writeValueAsString(body);
                requestBody = RequestBody.create(json, JSON);
                logger.debug("Request body: {}", json);
            } catch (JsonProcessingException e) {
                throw new KalshiApiException("Failed to serialize request body", e);
            }
        }

        switch (method.toUpperCase()) {
            case "GET":
                requestBuilder.get();
                break;
            case "POST":
                requestBuilder.post(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            case "DELETE":
                if (requestBody != null) {
                    requestBuilder.delete(requestBody);
                } else {
                    requestBuilder.delete();
                }
                break;
            case "PUT":
                requestBuilder.put(requestBody != null ? requestBody : RequestBody.create("", JSON));
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        Request request = requestBuilder.build();
        logger.debug("Executing {} {} ", method, path);

        try (Response response = httpClient.newCall(request).execute()) {
            return handleResponse(response, responseType);
        } catch (IOException e) {
            throw new KalshiApiException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handle the HTTP response.
     */
    @SuppressWarnings("unchecked")
    private <T> T handleResponse(Response response, Class<T> responseType) throws IOException {
        String responseBody = response.body() != null ? response.body().string() : "";
        int statusCode = response.code();

        logger.debug("Response status: {}, body length: {}", statusCode, responseBody.length());

        // Handle error responses
        if (!response.isSuccessful()) {
            handleErrorResponse(statusCode, responseBody);
        }

        // Return raw string if requested
        if (responseType == String.class) {
            return (T) responseBody;
        }

        // Return null for empty responses (e.g., 204 No Content)
        if (responseBody.isEmpty() || statusCode == 204) {
            return null;
        }

        // Parse JSON response
        try {
            return objectMapper.readValue(responseBody, responseType);
        } catch (JsonProcessingException e) {
            throw new KalshiApiException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    /**
     * Handle error responses and throw appropriate exceptions.
     */
    private void handleErrorResponse(int statusCode, String responseBody) {
        String errorMessage = "API request failed";
        String errorCode = null;

        try {
            JsonNode errorJson = objectMapper.readTree(responseBody);
            if (errorJson.has("error")) {
                JsonNode error = errorJson.get("error");
                if (error.has("message")) {
                    errorMessage = error.get("message").asText();
                }
                if (error.has("code")) {
                    errorCode = error.get("code").asText();
                }
            } else if (errorJson.has("message")) {
                errorMessage = errorJson.get("message").asText();
            }
        } catch (Exception ignored) {
            // Use default error message if parsing fails
        }

        switch (statusCode) {
            case 401:
            case 403:
                throw new AuthenticationException(errorMessage, statusCode, errorCode, responseBody);
            case 429:
                throw new RateLimitException(errorMessage, statusCode, errorCode, responseBody, 1000);
            case 400:
            case 422:
                throw new OrderException(errorMessage, statusCode, errorCode, responseBody);
            default:
                throw new KalshiApiException(errorMessage, statusCode, errorCode, responseBody);
        }
    }

    /**
     * Parse a JSON response using a dot-notation path.
     */
    private <T> T parseJsonPath(String json, String jsonPath, Class<T> responseType) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode targetNode = root;

            for (String part : jsonPath.split("\\.")) {
                targetNode = targetNode.get(part);
                if (targetNode == null) {
                    throw new KalshiApiException("JSON path not found: " + jsonPath);
                }
            }

            return objectMapper.treeToValue(targetNode, responseType);
        } catch (JsonProcessingException e) {
            throw new KalshiApiException("Failed to parse JSON path: " + jsonPath, e);
        }
    }

    /**
     * Get the object mapper for custom parsing needs.
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Get the base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for KalshiClient.
     */
    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URL;
        private KalshiAuthenticator authenticator;
        private OkHttpClient httpClient;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(30);

        /**
         * Set the base URL (default is production URL).
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Use the demo/sandbox environment.
         */
        public Builder useDemo() {
            this.baseUrl = DEMO_BASE_URL;
            return this;
        }

        /**
         * Set the authenticator.
         */
        public Builder authenticator(KalshiAuthenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        /**
         * Set authentication using API key ID and private key PEM string.
         */
        public Builder credentials(String apiKeyId, String privateKeyPem) {
            this.authenticator = KalshiAuthenticator.fromPem(apiKeyId, privateKeyPem);
            return this;
        }

        /**
         * Set authentication using API key ID and private key file.
         */
        public Builder credentialsFromFile(String apiKeyId, Path privateKeyPath) {
            this.authenticator = KalshiAuthenticator.fromFile(apiKeyId, privateKeyPath);
            return this;
        }

        /**
         * Set a custom OkHttpClient.
         */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Set connect timeout.
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout.
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set write timeout.
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * Build the client.
         * Note: Authenticator is optional for public endpoints (series, events, markets).
         * It is required for authenticated endpoints (orders, portfolio).
         */
        public KalshiClient build() {
            if (httpClient == null) {
                httpClient = new OkHttpClient.Builder()
                        .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .writeTimeout(writeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .build();
            }

            return new KalshiClient(this);
        }
    }

    /**
     * Check if the client has authentication configured.
     */
    public boolean hasAuthentication() {
        return authenticator != null;
    }
}
