package com.betting.etrade.subscription;

import com.betting.etrade.model.QuoteData;

import java.util.List;

/**
 * Callback interface for receiving quote updates.
 */
@FunctionalInterface
public interface QuoteCallback {

    /**
     * Called when new quotes are received.
     *
     * @param quotes the list of quote data received
     */
    void onQuotesReceived(List<QuoteData> quotes);
}
