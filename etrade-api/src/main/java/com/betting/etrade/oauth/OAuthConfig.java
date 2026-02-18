package com.betting.etrade.oauth;

/**
 * Configuration for E*TRADE OAuth authentication.
 */
public class OAuthConfig {

    private final String consumerKey;
    private final String consumerSecret;
    private final String baseUrl;
    private final String authorizeUrl;
    private final boolean sandbox;

    // Live environment URLs
    private static final String LIVE_BASE_URL = "https://api.etrade.com";
    private static final String LIVE_AUTHORIZE_URL = "https://us.etrade.com/e/t/etws/authorize";

    // Sandbox environment URLs
    private static final String SANDBOX_BASE_URL = "https://apisb.etrade.com";
    private static final String SANDBOX_AUTHORIZE_URL = "https://apisb.etrade.com/e/t/etws/authorize";

    private OAuthConfig(Builder builder) {
        this.consumerKey = builder.consumerKey;
        this.consumerSecret = builder.consumerSecret;
        this.sandbox = builder.sandbox;
        this.baseUrl = builder.sandbox ? SANDBOX_BASE_URL : LIVE_BASE_URL;
        this.authorizeUrl = builder.sandbox ? SANDBOX_AUTHORIZE_URL : LIVE_AUTHORIZE_URL;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public String getRequestTokenUrl() {
        return baseUrl + "/oauth/request_token";
    }

    public String getAccessTokenUrl() {
        return baseUrl + "/oauth/access_token";
    }

    public String getAuthorizationUrl(String token) {
        return authorizeUrl + "?key=" + consumerKey + "&token=" + token;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String consumerKey;
        private String consumerSecret;
        private boolean sandbox = false;

        public Builder consumerKey(String consumerKey) {
            this.consumerKey = consumerKey;
            return this;
        }

        public Builder consumerSecret(String consumerSecret) {
            this.consumerSecret = consumerSecret;
            return this;
        }

        public Builder sandbox(boolean sandbox) {
            this.sandbox = sandbox;
            return this;
        }

        public OAuthConfig build() {
            if (consumerKey == null || consumerKey.isEmpty()) {
                throw new IllegalArgumentException("Consumer key is required");
            }
            if (consumerSecret == null || consumerSecret.isEmpty()) {
                throw new IllegalArgumentException("Consumer secret is required");
            }
            return new OAuthConfig(this);
        }
    }
}
