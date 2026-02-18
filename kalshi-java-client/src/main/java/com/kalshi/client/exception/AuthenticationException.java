package com.kalshi.client.exception;

/**
 * Exception thrown when authentication fails.
 */
public class AuthenticationException extends KalshiApiException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthenticationException(String message, int statusCode, String errorCode, String responseBody) {
        super(message, statusCode, errorCode, responseBody);
    }
}
