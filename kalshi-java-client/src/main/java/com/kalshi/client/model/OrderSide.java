package com.kalshi.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Order side enum - yes or no.
 */
public enum OrderSide {
    YES("yes"),
    NO("no");

    private final String value;

    OrderSide(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static OrderSide fromValue(String value) {
        for (OrderSide side : values()) {
            if (side.value.equalsIgnoreCase(value)) {
                return side;
            }
        }
        throw new IllegalArgumentException("Unknown order side: " + value);
    }
}
