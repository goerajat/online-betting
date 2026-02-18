package com.betting.etrade.model;

/**
 * Quote status types from E*TRADE API.
 */
public enum QuoteStatus {
    REALTIME,
    DELAYED,
    CLOSING,
    EH_REALTIME,
    EH_BEFORE_OPEN,
    EH_CLOSED,
    INDICATIVE_REALTIME,
    INVALID
}
