package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Market status enum.
 */
public enum MarketStatus {
    INITIALIZED("initialized"),
    INACTIVE("inactive"),
    ACTIVE("active"),
    CLOSED("closed"),
    DETERMINED("determined"),
    DISPUTED("disputed"),
    AMENDED("amended"),
    FINALIZED("finalized"),
    UNOPENED("unopened"),
    OPEN("open"),
    PAUSED("paused"),
    SETTLED("settled");

    private final String value;

    MarketStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static MarketStatus fromValue(String value) {
        for (MarketStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown market status: " + value);
    }
}
