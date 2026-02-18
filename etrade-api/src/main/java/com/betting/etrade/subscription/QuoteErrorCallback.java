package com.betting.etrade.subscription;

/**
 * Callback interface for handling quote subscription errors.
 */
@FunctionalInterface
public interface QuoteErrorCallback {

    /**
     * Called when an error occurs during quote fetching.
     *
     * @param error the exception that occurred
     */
    void onError(Exception error);
}
