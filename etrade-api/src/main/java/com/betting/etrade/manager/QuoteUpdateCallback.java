package com.betting.etrade.manager;

import com.betting.etrade.model.QuoteData;

import java.util.List;

/**
 * Callback interface for receiving quote updates from MarketDataManager.
 */
@FunctionalInterface
public interface QuoteUpdateCallback {

    /**
     * Called when new quote data is received.
     *
     * @param quotes the list of updated quotes
     */
    void onQuoteUpdate(List<QuoteData> quotes);
}
