package com.kalshi.client.risk;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for risk checks on orders.
 * Supports global limits and per-strategy overrides.
 * All limits are optional - set to null or 0 to disable a specific check.
 *
 * <p>Example usage with global limits:</p>
 * <pre>{@code
 * RiskConfig config = RiskConfig.builder()
 *     .maxOrderQuantity(100)           // Max 100 contracts per order
 *     .maxOrderNotional(5000)          // Max $50.00 per order (in cents)
 *     .maxPositionQuantity(500)        // Max 500 contracts per position
 *     .maxPositionNotional(25000)      // Max $250.00 per position (in cents)
 *     .build();
 * }</pre>
 *
 * <p>Example usage with per-strategy limits:</p>
 * <pre>{@code
 * RiskConfig config = RiskConfig.builder()
 *     // Global defaults
 *     .maxOrderQuantity(100)
 *     .maxOrderNotional(5000)
 *     // Strategy-specific overrides
 *     .forStrategy("AggressiveStrategy")
 *         .maxOrderQuantity(50)        // Lower limit for aggressive strategy
 *         .maxOrderNotional(2500)
 *         .done()
 *     .forStrategy("ConservativeStrategy")
 *         .maxOrderQuantity(200)       // Higher limit for conservative strategy
 *         .maxPositionQuantity(1000)
 *         .done()
 *     .build();
 * }</pre>
 */
public class RiskConfig {

    private final Integer maxOrderQuantity;
    private final Integer maxOrderNotional;
    private final Integer maxPositionQuantity;
    private final Integer maxPositionNotional;
    private final boolean enabled;
    private final Map<String, StrategyLimits> strategyLimits;

    private RiskConfig(Builder builder) {
        this.maxOrderQuantity = builder.maxOrderQuantity;
        this.maxOrderNotional = builder.maxOrderNotional;
        this.maxPositionQuantity = builder.maxPositionQuantity;
        this.maxPositionNotional = builder.maxPositionNotional;
        this.enabled = builder.enabled;
        this.strategyLimits = new HashMap<>(builder.strategyLimits);
    }

    /**
     * Create a new builder for RiskConfig.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a disabled RiskConfig (no checks performed).
     */
    public static RiskConfig disabled() {
        return builder().enabled(false).build();
    }

    /**
     * Check if risk checks are enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the maximum quantity allowed per order (global default).
     */
    public Integer getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    /**
     * Get the maximum notional value allowed per order (global default, in cents).
     */
    public Integer getMaxOrderNotional() {
        return maxOrderNotional;
    }

    /**
     * Get the maximum quantity allowed per position (global default).
     */
    public Integer getMaxPositionQuantity() {
        return maxPositionQuantity;
    }

    /**
     * Get the maximum notional value allowed per position (global default, in cents).
     */
    public Integer getMaxPositionNotional() {
        return maxPositionNotional;
    }

    /**
     * Get max order quantity for a specific strategy (or global default).
     */
    public Integer getMaxOrderQuantity(String strategyName) {
        StrategyLimits limits = strategyLimits.get(strategyName);
        if (limits != null && limits.maxOrderQuantity != null) {
            return limits.maxOrderQuantity;
        }
        return maxOrderQuantity;
    }

    /**
     * Get max order notional for a specific strategy (or global default).
     */
    public Integer getMaxOrderNotional(String strategyName) {
        StrategyLimits limits = strategyLimits.get(strategyName);
        if (limits != null && limits.maxOrderNotional != null) {
            return limits.maxOrderNotional;
        }
        return maxOrderNotional;
    }

    /**
     * Get max position quantity for a specific strategy (or global default).
     */
    public Integer getMaxPositionQuantity(String strategyName) {
        StrategyLimits limits = strategyLimits.get(strategyName);
        if (limits != null && limits.maxPositionQuantity != null) {
            return limits.maxPositionQuantity;
        }
        return maxPositionQuantity;
    }

    /**
     * Get max position notional for a specific strategy (or global default).
     */
    public Integer getMaxPositionNotional(String strategyName) {
        StrategyLimits limits = strategyLimits.get(strategyName);
        if (limits != null && limits.maxPositionNotional != null) {
            return limits.maxPositionNotional;
        }
        return maxPositionNotional;
    }

    /**
     * Check if a strategy has custom limits configured.
     */
    public boolean hasStrategyLimits(String strategyName) {
        return strategyLimits.containsKey(strategyName);
    }

    /**
     * Check if max order quantity limit is set (for a strategy or globally).
     */
    public boolean hasMaxOrderQuantity(String strategyName) {
        Integer limit = getMaxOrderQuantity(strategyName);
        return limit != null && limit > 0;
    }

    /**
     * Check if max order notional limit is set (for a strategy or globally).
     */
    public boolean hasMaxOrderNotional(String strategyName) {
        Integer limit = getMaxOrderNotional(strategyName);
        return limit != null && limit > 0;
    }

    /**
     * Check if max position quantity limit is set (for a strategy or globally).
     */
    public boolean hasMaxPositionQuantity(String strategyName) {
        Integer limit = getMaxPositionQuantity(strategyName);
        return limit != null && limit > 0;
    }

    /**
     * Check if max position notional limit is set (for a strategy or globally).
     */
    public boolean hasMaxPositionNotional(String strategyName) {
        Integer limit = getMaxPositionNotional(strategyName);
        return limit != null && limit > 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RiskConfig{enabled=").append(enabled);
        sb.append(", maxOrderQuantity=").append(maxOrderQuantity);
        sb.append(", maxOrderNotional=").append(maxOrderNotional);
        sb.append(", maxPositionQuantity=").append(maxPositionQuantity);
        sb.append(", maxPositionNotional=").append(maxPositionNotional);
        if (!strategyLimits.isEmpty()) {
            sb.append(", strategyOverrides=").append(strategyLimits.keySet());
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Strategy-specific limit overrides.
     */
    public static class StrategyLimits {
        private Integer maxOrderQuantity;
        private Integer maxOrderNotional;
        private Integer maxPositionQuantity;
        private Integer maxPositionNotional;
    }

    /**
     * Builder for RiskConfig.
     */
    public static class Builder {
        private Integer maxOrderQuantity;
        private Integer maxOrderNotional;
        private Integer maxPositionQuantity;
        private Integer maxPositionNotional;
        private boolean enabled = true;
        private final Map<String, StrategyLimits> strategyLimits = new HashMap<>();

        /**
         * Set the maximum quantity allowed per order (global default).
         */
        public Builder maxOrderQuantity(int maxOrderQuantity) {
            this.maxOrderQuantity = maxOrderQuantity;
            return this;
        }

        /**
         * Set the maximum notional value allowed per order (global default, in cents).
         */
        public Builder maxOrderNotional(int maxOrderNotional) {
            this.maxOrderNotional = maxOrderNotional;
            return this;
        }

        /**
         * Set the maximum quantity allowed per position (global default).
         */
        public Builder maxPositionQuantity(int maxPositionQuantity) {
            this.maxPositionQuantity = maxPositionQuantity;
            return this;
        }

        /**
         * Set the maximum notional value allowed per position (global default, in cents).
         */
        public Builder maxPositionNotional(int maxPositionNotional) {
            this.maxPositionNotional = maxPositionNotional;
            return this;
        }

        /**
         * Enable or disable risk checks.
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Start configuring limits for a specific strategy.
         *
         * @param strategyName The strategy class simple name (e.g., "MyStrategy")
         * @return StrategyBuilder for configuring strategy-specific limits
         */
        public StrategyBuilder forStrategy(String strategyName) {
            return new StrategyBuilder(this, strategyName);
        }

        /**
         * Build the RiskConfig.
         */
        public RiskConfig build() {
            return new RiskConfig(this);
        }
    }

    /**
     * Builder for strategy-specific limits.
     */
    public static class StrategyBuilder {
        private final Builder parent;
        private final String strategyName;
        private final StrategyLimits limits = new StrategyLimits();

        StrategyBuilder(Builder parent, String strategyName) {
            this.parent = parent;
            this.strategyName = strategyName;
        }

        public StrategyBuilder maxOrderQuantity(int maxOrderQuantity) {
            limits.maxOrderQuantity = maxOrderQuantity;
            return this;
        }

        public StrategyBuilder maxOrderNotional(int maxOrderNotional) {
            limits.maxOrderNotional = maxOrderNotional;
            return this;
        }

        public StrategyBuilder maxPositionQuantity(int maxPositionQuantity) {
            limits.maxPositionQuantity = maxPositionQuantity;
            return this;
        }

        public StrategyBuilder maxPositionNotional(int maxPositionNotional) {
            limits.maxPositionNotional = maxPositionNotional;
            return this;
        }

        /**
         * Finish configuring this strategy and return to the main builder.
         */
        public Builder done() {
            parent.strategyLimits.put(strategyName, limits);
            return parent;
        }
    }
}
