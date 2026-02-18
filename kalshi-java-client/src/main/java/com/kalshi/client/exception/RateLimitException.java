package com.kalshi.client.exception;

/**
 * Exception thrown when rate limit is exceeded.
 */
public class RateLimitException extends KalshiApiException {

    private final long retryAfterMs;

    public RateLimitException(String message, long retryAfterMs) {
        super(message);
        this.retryAfterMs = retryAfterMs;
    }

    public RateLimitException(String message, int statusCode, String errorCode, String responseBody, long retryAfterMs) {
        super(message, statusCode, errorCode, responseBody);
        this.retryAfterMs = retryAfterMs;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
