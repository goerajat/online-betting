package com.kalshi.client.exception;

/**
 * Exception thrown when order operations fail.
 */
public class OrderException extends KalshiApiException {

    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }

    public OrderException(String message, int statusCode, String errorCode, String responseBody) {
        super(message, statusCode, errorCode, responseBody);
    }
}
