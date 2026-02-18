package com.betting.marketdata.api;

/**
 * Listener interface for handling market data errors.
 */
@FunctionalInterface
public interface MarketDataErrorListener {

    /**
     * Called when an error occurs while fetching market data.
     *
     * @param error the exception that occurred
     */
    void onError(Exception error);
}
