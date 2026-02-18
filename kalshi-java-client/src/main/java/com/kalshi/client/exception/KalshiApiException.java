package com.kalshi.client.exception;

/**
 * Base exception for Kalshi API errors.
 */
public class KalshiApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;
    private final String responseBody;

    public KalshiApiException(String message) {
        super(message);
        this.statusCode = 0;
        this.errorCode = null;
        this.responseBody = null;
    }

    public KalshiApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
        this.responseBody = null;
    }

    public KalshiApiException(String message, int statusCode, String errorCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return "KalshiApiException{" +
                "message='" + getMessage() + '\'' +
                ", statusCode=" + statusCode +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
