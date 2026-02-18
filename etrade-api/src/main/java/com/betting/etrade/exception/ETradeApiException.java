package com.betting.etrade.exception;

/**
 * Exception thrown when E*TRADE API calls fail.
 */
public class ETradeApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public ETradeApiException(String message) {
        super(message);
        this.httpStatus = 0;
        this.errorCode = null;
    }

    public ETradeApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.errorCode = null;
    }

    public ETradeApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        return "ETradeApiException{" +
                "httpStatus=" + httpStatus +
                ", errorCode='" + errorCode + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
