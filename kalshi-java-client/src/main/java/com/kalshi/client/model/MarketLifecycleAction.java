package com.kalshi.client.model;

/**
 * Actions that can occur in market lifecycle events.
 * These correspond to the market_lifecycle_v2 WebSocket channel messages.
 */
public enum MarketLifecycleAction {
    /**
     * A new market has been created.
     */
    CREATED("created"),

    /**
     * The market has been activated for trading.
     */
    ACTIVATED("activated"),

    /**
     * The market has been deactivated (trading suspended).
     */
    DEACTIVATED("deactivated"),

    /**
     * The market's close date has been updated.
     */
    CLOSE_DATE_UPDATED("close_date_updated"),

    /**
     * The market outcome has been determined (result known).
     */
    DETERMINED("determined"),

    /**
     * The market has been settled (payouts processed).
     */
    SETTLED("settled");

    private final String value;

    MarketLifecycleAction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse a string value to MarketLifecycleAction.
     *
     * @param value The string value from API
     * @return The corresponding enum value, or null if not found
     */
    public static MarketLifecycleAction fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (MarketLifecycleAction action : values()) {
            if (action.value.equalsIgnoreCase(value)) {
                return action;
            }
        }
        return null;
    }
}
