package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Time in force enum for orders.
 */
public enum TimeInForce {
    FILL_OR_KILL("fill_or_kill"),
    GOOD_TILL_CANCELED("good_till_canceled"),
    IMMEDIATE_OR_CANCEL("immediate_or_cancel");

    private final String value;

    TimeInForce(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static TimeInForce fromValue(String value) {
        for (TimeInForce tif : values()) {
            if (tif.value.equalsIgnoreCase(value)) {
                return tif;
            }
        }
        throw new IllegalArgumentException("Unknown time in force: " + value);
    }
}
