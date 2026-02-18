package com.betting.etrade.manager;

/**
 * Callback interface for handling errors in MarketDataManager.
 */
@FunctionalInterface
public interface MarketDataErrorCallback {

    /**
     * Called when an error occurs while fetching market data.
     *
     * @param error the exception that occurred
     */
    void onError(Exception error);
}
