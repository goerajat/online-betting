package com.kalshi.sample.strategy;

import com.kalshi.client.model.Market;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Predicate;

/**
 * Market filter for index event strategies.
 * Filters markets based on status, volume, expiration time, and open time.
 *
 * <p>This filter is designed for use with EventStrategy and checks:</p>
 * <ul>
 *   <li><b>Status</b>: Market must be "active" or "initialized"</li>
 *   <li><b>Volume</b>: Market must have minimum 24h trading volume (for active markets)</li>
 *   <li><b>Expiration</b>: Market must not expire within a minimum time threshold</li>
 *   <li><b>Open Time</b>: For "initialized" markets, must open within a maximum time threshold</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Using default configuration
 * Predicate<Market> filter = IndexMarketFilter.defaultFilter();
 *
 * // Using custom configuration
 * Predicate<Market> filter = IndexMarketFilter.builder()
 *     .minVolume24h(500)
 *     .minHoursToExpiration(4)
 *     .maxHoursUntilOpen(8)
 *     .requireActiveOrInitialized(true)
 *     .build();
 *
 * // Pass to EventStrategy constructor
 * super(eventTicker, filter);
 * }</pre>
 */
public class IndexMarketFilter implements Predicate<Market> {

    private static final Logger log = LoggerFactory.getLogger(IndexMarketFilter.class);

    // Default configuration values
    public static final int DEFAULT_MIN_VOLUME_24H = 100;
    public static final int DEFAULT_MIN_HOURS_TO_EXPIRATION = 2;
    public static final int DEFAULT_MAX_HOURS_UNTIL_OPEN = 8;
    public static final boolean DEFAULT_REQUIRE_ACTIVE_OR_INITIALIZED = true;

    private final int minVolume24h;
    private final int minHoursToExpiration;
    private final int maxHoursUntilOpen;
    private final boolean requireActiveOrInitialized;
    private boolean loggingEnabled = true;

    /**
     * Create a market filter with the specified parameters.
     *
     * @param minVolume24h              Minimum 24h trading volume required (0 to disable)
     * @param minHoursToExpiration      Minimum hours until expiration required (0 to disable)
     * @param maxHoursUntilOpen         Maximum hours until open for initialized markets (0 to disable)
     * @param requireActiveOrInitialized Whether to require market status to be "active" or "initialized"
     */
    public IndexMarketFilter(int minVolume24h, int minHoursToExpiration, int maxHoursUntilOpen,
                             boolean requireActiveOrInitialized) {
        this.minVolume24h = minVolume24h;
        this.minHoursToExpiration = minHoursToExpiration;
        this.maxHoursUntilOpen = maxHoursUntilOpen;
        this.requireActiveOrInitialized = requireActiveOrInitialized;
    }

    /**
     * Create a market filter with default parameters.
     * - Min 24h volume: 100
     * - Min hours to expiration: 2
     * - Max hours until open: 8 (for initialized markets)
     * - Require active or initialized: true
     */
    public IndexMarketFilter() {
        this(DEFAULT_MIN_VOLUME_24H, DEFAULT_MIN_HOURS_TO_EXPIRATION,
             DEFAULT_MAX_HOURS_UNTIL_OPEN, DEFAULT_REQUIRE_ACTIVE_OR_INITIALIZED);
    }

    @Override
    public boolean test(Market market) {
        String marketId = getMarketIdentifier(market);
        String status = market.getStatus();

        log.debug("Evaluating market: {} (status={}, volume24h={})",
                marketId, status, market.getVolume24h());

        // Check status - accept "active" or "initialized"
        if (requireActiveOrInitialized && !isActiveOrInitialized(market)) {
            logFilteredOut(marketId, "status",
                String.format("status='%s' (required: 'active' or 'initialized')", status));
            return false;
        }

        // For initialized markets, check if they open within the allowed time window
        if (isInitialized(market) && maxHoursUntilOpen > 0 && !opensWithinTimeWindow(market)) {
            Instant openTime = market.getOpenTime();
            Instant maxOpenTime = Instant.now().plus(maxHoursUntilOpen, ChronoUnit.HOURS);
            logFilteredOut(marketId, "openTime",
                String.format("openTime=%s (required: <= %s, maxHoursUntilOpen=%d)",
                    openTime, maxOpenTime, maxHoursUntilOpen));
            return false;
        }

        // Check minimum volume (only for active markets - initialized markets have no volume yet)
        if (isActive(market) && minVolume24h > 0 && !hasMinimumVolume(market)) {
            Long actualVolume = market.getVolume24h();
            logFilteredOut(marketId, "volume24h",
                String.format("volume24h=%s (required: >= %d)",
                    actualVolume != null ? actualVolume.toString() : "null", minVolume24h));
            return false;
        }

        // Check expiration time
        if (minHoursToExpiration > 0 && !hasEnoughTimeToExpiration(market)) {
            Instant expiration = market.getExpectedExpirationTime();
            Instant minExpiration = Instant.now().plus(minHoursToExpiration, ChronoUnit.HOURS);
            logFilteredOut(marketId, "expiration",
                String.format("expectedExpiration=%s (required: >= %s, minHoursToExpiration=%d)",
                    expiration, minExpiration, minHoursToExpiration));
            return false;
        }

        if (loggingEnabled) {
            log.info("MARKET ACCEPTED: {} (status={}) - passed all filter criteria", marketId, status);
        }
        return true;
    }

    /**
     * Get a human-readable identifier for the market.
     */
    private String getMarketIdentifier(Market market) {
        String ticker = market.getTicker();
        String subtitle = market.getSubtitle();
        if (subtitle != null && !subtitle.isEmpty()) {
            return String.format("%s (%s)", ticker, subtitle);
        }
        String title = market.getTitle();
        if (title != null && !title.isEmpty()) {
            if (title.length() > 40) {
                title = title.substring(0, 37) + "...";
            }
            return String.format("%s (%s)", ticker, title);
        }
        return ticker;
    }

    /**
     * Log the reason a market was filtered out.
     */
    private void logFilteredOut(String marketId, String filterField, String details) {
        if (loggingEnabled) {
            log.info("MARKET FILTERED OUT: {} | field: {} | {}", marketId, filterField, details);
        }
    }

    /**
     * Enable or disable filter logging.
     * @param enabled true to enable logging, false to disable
     * @return this filter for chaining
     */
    public IndexMarketFilter withLogging(boolean enabled) {
        this.loggingEnabled = enabled;
        return this;
    }

    /**
     * Check if the market status is "active".
     */
    private boolean isActive(Market market) {
        return "active".equalsIgnoreCase(market.getStatus());
    }

    /**
     * Check if the market status is "initialized".
     */
    private boolean isInitialized(Market market) {
        return "initialized".equalsIgnoreCase(market.getStatus());
    }

    /**
     * Check if the market status is "active" or "initialized".
     */
    private boolean isActiveOrInitialized(Market market) {
        return isActive(market) || isInitialized(market);
    }

    /**
     * Check if an initialized market opens within the allowed time window.
     */
    private boolean opensWithinTimeWindow(Market market) {
        Instant openTime = market.getOpenTime();
        if (openTime == null) {
            // If no open time is set, allow the market
            return true;
        }
        Instant maxOpenTime = Instant.now().plus(maxHoursUntilOpen, ChronoUnit.HOURS);
        return !openTime.isAfter(maxOpenTime);
    }

    /**
     * Check if the market has sufficient 24h trading volume.
     */
    private boolean hasMinimumVolume(Market market) {
        Long volume24h = market.getVolume24h();
        return volume24h != null && volume24h >= minVolume24h;
    }

    /**
     * Check if the market has enough time until expiration.
     */
    private boolean hasEnoughTimeToExpiration(Market market) {
        Instant expiration = market.getExpectedExpirationTime();
        if (expiration == null) {
            // If no expiration time is set, allow the market
            return true;
        }
        Instant minExpiration = Instant.now().plus(minHoursToExpiration, ChronoUnit.HOURS);
        return !expiration.isBefore(minExpiration);
    }

    // ==================== Getters ====================

    /**
     * Get the minimum 24h volume threshold.
     */
    public int getMinVolume24h() {
        return minVolume24h;
    }

    /**
     * Get the minimum hours to expiration threshold.
     */
    public int getMinHoursToExpiration() {
        return minHoursToExpiration;
    }

    /**
     * Get the maximum hours until open for initialized markets.
     */
    public int getMaxHoursUntilOpen() {
        return maxHoursUntilOpen;
    }

    /**
     * Check if active or initialized status is required.
     */
    public boolean isRequireActiveOrInitialized() {
        return requireActiveOrInitialized;
    }

    // ==================== Factory Methods ====================

    /**
     * Create a filter with default configuration.
     */
    public static IndexMarketFilter defaultFilter() {
        return new IndexMarketFilter();
    }

    /**
     * Create a filter that only checks for active/initialized status (no volume/expiration requirements).
     */
    public static IndexMarketFilter activeOrInitializedOnly() {
        return new IndexMarketFilter(0, 0, DEFAULT_MAX_HOURS_UNTIL_OPEN, true);
    }

    /**
     * Create a filter with no restrictions (accepts all markets).
     */
    public static IndexMarketFilter acceptAll() {
        return new IndexMarketFilter(0, 0, 0, false);
    }

    /**
     * Create a filter for high-volume markets only.
     *
     * @param minVolume24h Minimum 24h trading volume
     */
    public static IndexMarketFilter highVolume(int minVolume24h) {
        return new IndexMarketFilter(minVolume24h, DEFAULT_MIN_HOURS_TO_EXPIRATION,
                                     DEFAULT_MAX_HOURS_UNTIL_OPEN, DEFAULT_REQUIRE_ACTIVE_OR_INITIALIZED);
    }

    /**
     * Create a builder for custom filter configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== Builder ====================

    /**
     * Builder for creating customized IndexMarketFilter instances.
     */
    public static class Builder {
        private int minVolume24h = DEFAULT_MIN_VOLUME_24H;
        private int minHoursToExpiration = DEFAULT_MIN_HOURS_TO_EXPIRATION;
        private int maxHoursUntilOpen = DEFAULT_MAX_HOURS_UNTIL_OPEN;
        private boolean requireActiveOrInitialized = DEFAULT_REQUIRE_ACTIVE_OR_INITIALIZED;
        private boolean loggingEnabled = true;

        /**
         * Set the minimum 24h trading volume required.
         *
         * @param minVolume24h Minimum volume (0 to disable volume check)
         */
        public Builder minVolume24h(int minVolume24h) {
            this.minVolume24h = minVolume24h;
            return this;
        }

        /**
         * Set the minimum hours until expiration required.
         *
         * @param hours Minimum hours (0 to disable expiration check)
         */
        public Builder minHoursToExpiration(int hours) {
            this.minHoursToExpiration = hours;
            return this;
        }

        /**
         * Set the maximum hours until open for initialized markets.
         *
         * @param hours Maximum hours (0 to disable open time check)
         */
        public Builder maxHoursUntilOpen(int hours) {
            this.maxHoursUntilOpen = hours;
            return this;
        }

        /**
         * Set whether to require the market to be active or initialized.
         *
         * @param require true to filter out markets that are not active or initialized
         */
        public Builder requireActiveOrInitialized(boolean require) {
            this.requireActiveOrInitialized = require;
            return this;
        }

        /**
         * Enable or disable filter logging.
         *
         * @param enabled true to log filtered markets, false to disable logging
         */
        public Builder logging(boolean enabled) {
            this.loggingEnabled = enabled;
            return this;
        }

        /**
         * Build the IndexMarketFilter with the configured parameters.
         */
        public IndexMarketFilter build() {
            IndexMarketFilter filter = new IndexMarketFilter(minVolume24h, minHoursToExpiration,
                                         maxHoursUntilOpen, requireActiveOrInitialized);
            filter.loggingEnabled = this.loggingEnabled;
            log.info("IndexMarketFilter created: {}, loggingEnabled={}", filter, loggingEnabled);
            return filter;
        }
    }

    @Override
    public String toString() {
        return "IndexMarketFilter{" +
                "minVolume24h=" + minVolume24h +
                ", minHoursToExpiration=" + minHoursToExpiration +
                ", maxHoursUntilOpen=" + maxHoursUntilOpen +
                ", requireActiveOrInitialized=" + requireActiveOrInitialized +
                '}';
    }
}
