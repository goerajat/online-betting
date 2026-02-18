package com.kalshi.client.risk;

/**
 * Exception thrown when a risk check fails.
 * Contains details about which check failed and the relevant values.
 */
public class RiskCheckException extends RuntimeException {

    private final RiskCheckType checkType;
    private final Number actualValue;
    private final Number limitValue;
    private final String ticker;

    /**
     * Create a new RiskCheckException.
     *
     * @param checkType The type of check that failed
     * @param message Descriptive error message
     * @param actualValue The actual value that exceeded the limit
     * @param limitValue The configured limit
     * @param ticker The market ticker (if applicable)
     */
    public RiskCheckException(RiskCheckType checkType, String message,
                               Number actualValue, Number limitValue, String ticker) {
        super(message);
        this.checkType = checkType;
        this.actualValue = actualValue;
        this.limitValue = limitValue;
        this.ticker = ticker;
    }

    /**
     * Create a new RiskCheckException without ticker.
     */
    public RiskCheckException(RiskCheckType checkType, String message,
                               Number actualValue, Number limitValue) {
        this(checkType, message, actualValue, limitValue, null);
    }

    /**
     * Get the type of risk check that failed.
     */
    public RiskCheckType getCheckType() {
        return checkType;
    }

    /**
     * Get the actual value that exceeded the limit.
     */
    public Number getActualValue() {
        return actualValue;
    }

    /**
     * Get the configured limit value.
     */
    public Number getLimitValue() {
        return limitValue;
    }

    /**
     * Get the market ticker (if applicable).
     */
    public String getTicker() {
        return ticker;
    }

    /**
     * Types of risk checks.
     */
    public enum RiskCheckType {
        MAX_ORDER_QUANTITY("Maximum order quantity exceeded"),
        MAX_ORDER_NOTIONAL("Maximum order notional exceeded"),
        MAX_POSITION_QUANTITY("Maximum position quantity exceeded"),
        MAX_POSITION_NOTIONAL("Maximum position notional exceeded");

        private final String description;

        RiskCheckType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
