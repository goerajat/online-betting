package com.kalshi.sample.strategy;

import com.betting.marketdata.api.MarketDataManager;
import com.betting.marketdata.api.QuoteListener;
import com.betting.marketdata.model.Quote;
import com.kalshi.client.config.StrategyConfig;
import com.kalshi.client.manager.ManagedMarket;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.strategy.EventStrategy;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Example EventStrategy implementation that operates on an index event (e.g., S&P 500 range markets).
 * Demonstrates how to filter markets, react to orderbook updates, and implement trading logic.
 *
 * <p>This strategy:</p>
 * <ul>
 *   <li>Tracks only active markets with reasonable volume (using {@link IndexMarketFilter})</li>
 *   <li>Filters out markets that expire too soon</li>
 *   <li>Logs orderbook updates and spread opportunities</li>
 *   <li>Provides example methods for placing orders on tracked markets</li>
 * </ul>
 *
 * <p>Run with: java ... --strategy=com.kalshi.sample.strategy.IndexEventStrategy</p>
 *
 * <p>Note: This is a demonstration strategy. Risk checks are configured and enforced
 * by the application, not by this strategy.</p>
 *
 * @see IndexMarketFilter
 */
public class IndexEventStrategy extends EventStrategy {

    // Default configuration values
    private static final String DEFAULT_EVENT_TICKER = "KXINX-25JAN13";
    private static final int DEFAULT_MIN_VOLUME_24H = 100;
    private static final int DEFAULT_MIN_HOURS_TO_EXPIRATION = 2;
    private static final int DEFAULT_WIDE_SPREAD_THRESHOLD = 5;

    // Instance configuration (from properties or defaults)
    private final int minVolume24h;
    private final int minHoursToExpiration;
    private final int maxHoursUntilOpen;
    private final int wideSpreadThreshold;

    // State
    private int orderbookUpdateCount = 0;
    private ManagedMarket bestSpreadMarket = null;
    private Integer lowestSpread = null;

    // Market data subscription (external data like E*TRADE)
    private final String marketDataSymbol;
    private String marketDataSubscriptionId;
    private volatile Double lastPrice;
    private final QuoteListener quoteListener;

    /**
     * Create the strategy for the default event ticker with default settings.
     */
    public IndexEventStrategy() {
        this(DEFAULT_EVENT_TICKER, (StrategyConfig) null);
    }

    /**
     * Create a quote listener that updates the market data label.
     */
    private QuoteListener createQuoteListener() {
        return quotes -> {
            if (quotes == null || quotes.isEmpty()) {
                return;
            }
            for (Quote quote : quotes) {
                if (quote.getSymbol() != null && quote.getSymbol().equalsIgnoreCase(marketDataSymbol)) {
                    updateMarketDataLabelFromQuote(quote);
                    break;
                }
            }
        };
    }

    /**
     * Update the market data label from a quote.
     * Format: "SYM: price +delta" or "SYM: price -delta"
     */
    private void updateMarketDataLabelFromQuote(Quote quote) {
        Double currentPrice = quote.getLastPrice();
        if (currentPrice == null) {
            return;
        }

        Double previousPrice = lastPrice;
        lastPrice = currentPrice;

        // Calculate delta from last tick
        String deltaStr;
        if (previousPrice != null) {
            double delta = currentPrice - previousPrice;
            if (delta >= 0) {
                deltaStr = String.format("+%.2f", delta);
            } else {
                deltaStr = String.format("%.2f", delta);
            }
        } else {
            // First tick - no delta yet
            deltaStr = "";
        }

        // Format label: "SYM: price +delta" or "SYM: price" (first tick)
        String label;
        if (deltaStr.isEmpty()) {
            label = String.format("%s: $%.2f", marketDataSymbol, currentPrice);
        } else {
            label = String.format("%s: $%.2f %s", marketDataSymbol, currentPrice, deltaStr);
        }

        setMarketDataLabel(label);
        log.debug("Market data label updated: {}", label);
    }

    /**
     * Constructor with event ticker and configuration from properties file.
     *
     * @param eventTicker The event ticker to trade
     * @param config      Configuration from properties file (can be null for defaults)
     */
    public IndexEventStrategy(String eventTicker, StrategyConfig config) {
        super(eventTicker, buildMarketFilter(config));

        // Don't fail if no markets pass the filter - strategy will just have 0 tracked markets
        setFailOnNoMarkets(false);

        // Store config values for logging/reference
        if (config != null) {
            this.minVolume24h = config.getMarketFilterMinVolume24h() != null
                    ? config.getMarketFilterMinVolume24h() : DEFAULT_MIN_VOLUME_24H;
            this.minHoursToExpiration = config.getMarketFilterMinHoursToExpiration() != null
                    ? config.getMarketFilterMinHoursToExpiration() : DEFAULT_MIN_HOURS_TO_EXPIRATION;
            this.maxHoursUntilOpen = config.getMarketFilterMaxHoursUntilOpen() != null
                    ? config.getMarketFilterMaxHoursUntilOpen() : IndexMarketFilter.DEFAULT_MAX_HOURS_UNTIL_OPEN;
            this.wideSpreadThreshold = config.getWideSpreadThreshold() != null
                    ? config.getWideSpreadThreshold() : DEFAULT_WIDE_SPREAD_THRESHOLD;
            this.marketDataSymbol = config.getMarketDataSymbol();
        } else {
            this.minVolume24h = DEFAULT_MIN_VOLUME_24H;
            this.minHoursToExpiration = DEFAULT_MIN_HOURS_TO_EXPIRATION;
            this.maxHoursUntilOpen = IndexMarketFilter.DEFAULT_MAX_HOURS_UNTIL_OPEN;
            this.wideSpreadThreshold = DEFAULT_WIDE_SPREAD_THRESHOLD;
            this.marketDataSymbol = null;
        }

        // Create quote listener for market data updates
        this.quoteListener = createQuoteListener();
    }

    /**
     * Alternative constructor to specify event ticker at runtime with default settings.
     */
    public IndexEventStrategy(String eventTicker) {
        this(eventTicker, (StrategyConfig) null);
    }

    /**
     * Alternative constructor with custom market filter.
     *
     * @param eventTicker  The event ticker to trade
     * @param marketFilter Custom market filter configuration
     */
    public IndexEventStrategy(String eventTicker, IndexMarketFilter marketFilter) {
        super(eventTicker, marketFilter);
        setFailOnNoMarkets(false);
        this.minVolume24h = marketFilter.getMinVolume24h();
        this.minHoursToExpiration = marketFilter.getMinHoursToExpiration();
        this.maxHoursUntilOpen = marketFilter.getMaxHoursUntilOpen();
        this.wideSpreadThreshold = DEFAULT_WIDE_SPREAD_THRESHOLD;
        this.marketDataSymbol = null;
        this.quoteListener = createQuoteListener();
    }

    /**
     * Build market filter from configuration.
     */
    private static IndexMarketFilter buildMarketFilter(StrategyConfig config) {
        IndexMarketFilter.Builder builder = IndexMarketFilter.builder();

        if (config != null) {
            if (config.getMarketFilterMinVolume24h() != null) {
                builder.minVolume24h(config.getMarketFilterMinVolume24h());
            }
            if (config.getMarketFilterMinHoursToExpiration() != null) {
                builder.minHoursToExpiration(config.getMarketFilterMinHoursToExpiration());
            }
            if (config.getMarketFilterMaxHoursUntilOpen() != null) {
                builder.maxHoursUntilOpen(config.getMarketFilterMaxHoursUntilOpen());
            }
            if (config.getMarketFilterRequireActiveOrInitialized() != null) {
                builder.requireActiveOrInitialized(config.getMarketFilterRequireActiveOrInitialized());
            }
        }

        return builder.build();
    }

    // ==================== Configuration ====================

    @Override
    protected String getMarketFilterDescription() {
        return String.format("IndexMarketFilter[minVolume24h=%d, minHoursToExpiration=%d, maxHoursUntilOpen=%d]",
                minVolume24h, minHoursToExpiration, maxHoursUntilOpen);
    }

    @Override
    protected boolean shouldTrackMarket(Market market) {
        // Track markets that are either active or initialized
        String status = market.getStatus();
        return "active".equalsIgnoreCase(status) || "initialized".equalsIgnoreCase(status);
    }

    // ==================== Strategy Lifecycle ====================

    @Override
    protected void onEventLoaded(Event event) {
        log.info("Event loaded: {}", event.getTitle());
        log.info("  Category: {}", event.getCategory());
        log.info("  Strike Date: {}", event.getStrikeDate());
        log.info("  Total Markets: {}", event.getMarkets() != null ? event.getMarkets().size() : 0);
    }

    @Override
    protected void onMarketsSubscribed(int marketCount) {
        log.info("Subscribed to {} markets", marketCount);

        // Log the tracked markets
        for (Market market : getTrackedMarketInfo()) {
            log.info("  - {}: {} (vol24h={})", market.getTicker(), market.getTitle(), market.getVolume24h());
        }
    }

    @Override
    protected void onStrategyReady() {
        log.info("Strategy ready!");
        log.info("Configuration:");
        log.info("  Event: {}", getEventTicker());
        log.info("  Min Volume 24h: {}", minVolume24h);
        log.info("  Min Hours to Expiration: {}", minHoursToExpiration);
        log.info("  Max Hours Until Open: {}", maxHoursUntilOpen);
        log.info("  Wide Spread Threshold: {}c", wideSpreadThreshold);
        log.info("  Market Data Symbol: {}", marketDataSymbol != null ? marketDataSymbol : "none");
        log.info("Tracking {} markets", getTrackedMarketCount());
    }

    @Override
    protected void onActivated() {
        log.info("Strategy activated");

        // Subscribe to market data for configured symbol
        subscribeToMarketData();
    }

    @Override
    protected void onDeactivated() {
        log.info("Strategy deactivated");

        // Unsubscribe from market data
        unsubscribeFromMarketData();

        // Clear the market data label
        setMarketDataLabel("");
        lastPrice = null;
    }

    /**
     * Subscribe to external market data for the configured symbol.
     */
    private void subscribeToMarketData() {
        if (marketDataSymbol == null || marketDataSymbol.isEmpty()) {
            log.debug("No market data symbol configured - skipping subscription");
            return;
        }

        MarketDataManager mdm = getMarketDataManager();
        if (mdm == null) {
            log.warn("MarketDataManager not available - cannot subscribe to {}", marketDataSymbol);
            return;
        }

        if (!mdm.isAuthenticated()) {
            log.warn("MarketDataManager not authenticated - cannot subscribe to {}", marketDataSymbol);
            return;
        }

        try {
            marketDataSubscriptionId = mdm.subscribe(
                    Collections.singletonList(marketDataSymbol),
                    quoteListener
            );
            log.info("Subscribed to market data for {} (subscriptionId={})", marketDataSymbol, marketDataSubscriptionId);
            logActivity("Subscribed to market data: " + marketDataSymbol);
        } catch (Exception e) {
            log.error("Failed to subscribe to market data for {}: {}", marketDataSymbol, e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe from external market data.
     */
    private void unsubscribeFromMarketData() {
        if (marketDataSubscriptionId == null) {
            return;
        }

        MarketDataManager mdm = getMarketDataManager();
        if (mdm == null) {
            marketDataSubscriptionId = null;
            return;
        }

        try {
            boolean unsubscribed = mdm.unsubscribe(marketDataSubscriptionId);
            if (unsubscribed) {
                log.info("Unsubscribed from market data for {} (subscriptionId={})", marketDataSymbol, marketDataSubscriptionId);
                logActivity("Unsubscribed from market data: " + marketDataSymbol);
            }
        } catch (Exception e) {
            log.error("Failed to unsubscribe from market data: {}", e.getMessage(), e);
        } finally {
            marketDataSubscriptionId = null;
        }
    }

    // ==================== Market Data Callbacks ====================

    @Override
    protected void onMarketDataUpdate(ManagedMarket market) {
        orderbookUpdateCount++;

        Integer spread = market.getYesSpread();
        Integer bestBid = market.getBestYesBid();
        Integer bestAsk = market.getBestYesAsk();

        // Track best spread opportunity
        if (spread != null && (lowestSpread == null || spread < lowestSpread)) {
            lowestSpread = spread;
            bestSpreadMarket = market;
        }

        // Log wide spreads (potential market-making opportunities)
        if (spread != null && spread > wideSpreadThreshold) {
            log.debug("Wide spread on {}: {}c (bid={}c, ask={}c)", market.getTicker(), spread, bestBid, bestAsk);
        }
    }

    @Override
    protected void onMarketInfoUpdate(ManagedMarket market) {
        log.info("Market info updated: {} - {}", market.getTicker(), market.getTitle());
    }

    // ==================== Timer Callback ====================

    @Override
    public void onTimer() {
        // Log periodic status
        log.debug("Timer tick - {} orderbook updates received", orderbookUpdateCount);

        // Find and log best spread opportunity
        findBestSpreadOpportunity();

        // Log position summary for tracked markets
        logPositionSummary();

        // Reset counter
        orderbookUpdateCount = 0;
    }

    @Override
    protected long getTimerIntervalSeconds() {
        return 10; // Check every 10 seconds
    }

    // ==================== Order Callbacks ====================

    @Override
    public void onOrderCreated(Order order) {
        // Only log if it's for a tracked market
        if (isTracking(order.getTicker())) {
            log.info("Order created on tracked market: {} - {} {} @ {}c",
                order.getTicker(), order.getAction(), order.getSide(),
                order.getYesPrice() != null ? order.getYesPrice() : order.getNoPrice());
        }
    }

    @Override
    public void onOrderRemoved(Order order) {
        if (isTracking(order.getTicker())) {
            log.info("Order removed on tracked market: {}", order.getTicker());
        }
    }

    // ==================== Position Callbacks ====================

    @Override
    public void onPositionOpened(Position position) {
        if (isTracking(position.getMarketTicker())) {
            log.info("Position opened on tracked market: {} - {} contracts",
                position.getMarketTicker(), position.getPosition());
        }
    }

    @Override
    public void onPositionUpdated(Position position) {
        if (isTracking(position.getMarketTicker())) {
            log.info("Position updated on tracked market: {} - {} contracts, P&L: ${}",
                position.getMarketTicker(), position.getPosition(), position.getRealizedPnlDollars());
        }
    }

    // ==================== Strategy Logic ====================

    /**
     * Find the market with the best (lowest) spread.
     */
    private void findBestSpreadOpportunity() {
        ManagedMarket best = null;
        Integer bestSpread = null;

        for (ManagedMarket market : getTrackedMarkets()) {
            Integer spread = market.getYesSpread();
            if (spread != null && (bestSpread == null || spread < bestSpread)) {
                bestSpread = spread;
                best = market;
            }
        }

        if (best != null && bestSpread != null) {
            log.debug("Best spread opportunity: {} with {}c spread (bid={}c, ask={}c)",
                best.getTicker(), bestSpread, best.getBestYesBid(), best.getBestYesAsk());
        }
    }

    /**
     * Log summary of positions in tracked markets.
     */
    private void logPositionSummary() {
        int positionCount = 0;
        for (Position pos : getPositionManager().getAllPositions()) {
            if (isTracking(pos.getMarketTicker())) {
                positionCount++;
            }
        }
        if (positionCount > 0) {
            log.debug("Positions in tracked markets: {}", positionCount);
        }
    }

    /**
     * Get markets sorted by spread (ascending).
     */
    public List<ManagedMarket> getMarketsBySpread() {
        List<ManagedMarket> markets = getTrackedMarkets();
        markets.sort(Comparator.comparingInt(m -> {
            Integer spread = m.getYesSpread();
            return spread != null ? spread : Integer.MAX_VALUE;
        }));
        return markets;
    }

    /**
     * Get markets sorted by yes bid (descending - highest implied probability first).
     */
    public List<ManagedMarket> getMarketsByYesBid() {
        List<ManagedMarket> markets = getTrackedMarkets();
        markets.sort((a, b) -> {
            Integer bidA = a.getBestYesBid();
            Integer bidB = b.getBestYesBid();
            if (bidA == null) return 1;
            if (bidB == null) return -1;
            return bidB.compareTo(bidA);
        });
        return markets;
    }

    // ==================== Example Trading Methods ====================

    /**
     * Example: Place a buy order on the market with the tightest spread.
     * Risk checks are automatically performed by the application.
     */
    public void buyBestSpreadMarket() {
        List<ManagedMarket> bySpread = getMarketsBySpread();
        if (bySpread.isEmpty()) {
            log.warn("No markets available");
            return;
        }

        ManagedMarket best = bySpread.get(0);
        Integer bestAsk = best.getBestYesAsk();

        if (bestAsk == null) {
            log.warn("No ask available for {}", best.getTicker());
            return;
        }

        try {
            // Buy 1 yes contract at the current ask
            Order order = buy(best.getTicker(), "yes", 1, bestAsk);
            log.info("Placed order: {} on {}", order.getOrderId(), best.getTicker());
        } catch (com.kalshi.client.risk.RiskCheckException e) {
            log.warn("Order rejected by risk check: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to place order: {}", e.getMessage(), e);
        }
    }

    /**
     * Example: Cancel all orders on tracked markets.
     */
    public void cancelAllTrackedOrders() {
        for (Order order : getOrderManager().getAllOrders()) {
            if (isTracking(order.getTicker())) {
                try {
                    cancelOrder(order.getOrderId());
                    log.info("Canceled order: {}", order.getOrderId());
                } catch (Exception e) {
                    log.error("Failed to cancel order {}: {}", order.getOrderId(), e.getMessage(), e);
                }
            }
        }
    }
}
