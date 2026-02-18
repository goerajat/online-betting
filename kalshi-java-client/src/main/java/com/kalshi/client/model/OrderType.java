package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Order type enum - limit or market.
 */
public enum OrderType {
    LIMIT("limit"),
    MARKET("market");

    private final String value;

    OrderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static OrderType fromValue(String value) {
        for (OrderType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown order type: " + value);
    }
}
