package com.betting.marketdata.api;

import com.betting.marketdata.model.Quote;

import java.util.List;

/**
 * Listener interface for receiving quote updates.
 */
@FunctionalInterface
public interface QuoteListener {

    /**
     * Called when new quotes are received.
     *
     * @param quotes the list of updated quotes
     */
    void onQuotes(List<Quote> quotes);
}
