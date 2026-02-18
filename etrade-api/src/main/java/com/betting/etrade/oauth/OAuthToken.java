package com.betting.etrade.oauth;

/**
 * Represents an OAuth token (request token or access token).
 */
public class OAuthToken {

    private final String token;
    private final String tokenSecret;

    public OAuthToken(String token, String tokenSecret) {
        this.token = token;
        this.tokenSecret = tokenSecret;
    }

    public String getToken() {
        return token;
    }

    public String getTokenSecret() {
        return tokenSecret;
    }

    @Override
    public String toString() {
        return "OAuthToken{token='" + token + "', tokenSecret='[REDACTED]'}";
    }
}
