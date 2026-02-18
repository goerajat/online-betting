package com.kalshi.client.risk;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a risk check violation event.
 * Used for logging and displaying risk violations in the UI.
 */
public class RiskViolation {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final String strategyName;
    private final RiskCheckException.RiskCheckType limitType;
    private final long attemptedValue;
    private final long limitValue;
    private final String ticker;
    private final String message;

    public RiskViolation(String strategyName, RiskCheckException.RiskCheckType limitType,
                         long attemptedValue, long limitValue, String ticker, String message) {
        this.timestamp = Instant.now();
        this.strategyName = strategyName;
        this.limitType = limitType;
        this.attemptedValue = attemptedValue;
        this.limitValue = limitValue;
        this.ticker = ticker;
        this.message = message;
    }

    /**
     * Create a RiskViolation from a RiskCheckException.
     */
    public static RiskViolation fromException(RiskCheckException ex, String strategyName) {
        return new RiskViolation(
                strategyName,
                ex.getCheckType(),
                ex.getActualValue().longValue(),
                ex.getLimitValue().longValue(),
                ex.getTicker(),
                ex.getMessage()
        );
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public RiskCheckException.RiskCheckType getLimitType() {
        return limitType;
    }

    public long getAttemptedValue() {
        return attemptedValue;
    }

    public long getLimitValue() {
        return limitValue;
    }

    public String getTicker() {
        return ticker;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Get formatted timestamp string.
     */
    public String getFormattedTime() {
        return TIME_FORMATTER.format(timestamp);
    }

    /**
     * Get a short description of the limit type.
     */
    public String getLimitTypeDescription() {
        switch (limitType) {
            case MAX_ORDER_QUANTITY:
                return "Order Qty";
            case MAX_ORDER_NOTIONAL:
                return "Order Notional";
            case MAX_POSITION_QUANTITY:
                return "Position Qty";
            case MAX_POSITION_NOTIONAL:
                return "Position Notional";
            default:
                return limitType.toString();
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s (attempted: %d, limit: %d)%s",
                getFormattedTime(),
                strategyName != null ? strategyName : "Global",
                getLimitTypeDescription(),
                attemptedValue,
                limitValue,
                ticker != null ? " [" + ticker + "]" : "");
    }
}
