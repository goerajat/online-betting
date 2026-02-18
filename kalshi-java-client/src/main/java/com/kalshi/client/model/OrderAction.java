package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Order action enum - buy or sell.
 */
public enum OrderAction {
    BUY("buy"),
    SELL("sell");

    private final String value;

    OrderAction(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static OrderAction fromValue(String value) {
        for (OrderAction action : values()) {
            if (action.value.equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown order action: " + value);
    }
}
