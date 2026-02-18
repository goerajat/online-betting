package com.betting.marketdata.model;

import java.time.Instant;

/**
 * Represents a market quote for a financial instrument.
 */
public interface Quote {

    /**
     * Get the symbol/ticker for this quote.
     */
    String getSymbol();

    /**
     * Get the last trade price.
     */
    Double getLastPrice();

    /**
     * Get the bid price.
     */
    Double getBid();

    /**
     * Get the ask price.
     */
    Double getAsk();

    /**
     * Get the bid size.
     */
    Long getBidSize();

    /**
     * Get the ask size.
     */
    Long getAskSize();

    /**
     * Get the price change from previous close.
     */
    Double getChange();

    /**
     * Get the percentage change from previous close.
     */
    Double getChangePercent();

    /**
     * Get the day's high price.
     */
    Double getHigh();

    /**
     * Get the day's low price.
     */
    Double getLow();

    /**
     * Get the day's open price.
     */
    Double getOpen();

    /**
     * Get the previous close price.
     */
    Double getPreviousClose();

    /**
     * Get the trading volume.
     */
    Long getVolume();

    /**
     * Get the quote timestamp.
     */
    Instant getTimestamp();

    /**
     * Get the quote status (e.g., REALTIME, DELAYED).
     */
    String getStatus();

    /**
     * Check if this is a real-time quote.
     */
    boolean isRealTime();
}
