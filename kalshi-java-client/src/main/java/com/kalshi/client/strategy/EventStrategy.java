package com.kalshi.client.strategy;

import com.kalshi.client.manager.ManagedMarket;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Abstract strategy that operates on a single Event and its associated markets.
 * Provides infrastructure for filtering, tracking, and subscribing to market data.
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Loads an Event by ticker and its associated markets</li>
 *   <li>Filters markets using {@link #shouldTrackMarket(Market)} or a custom predicate</li>
 *   <li>Subscribes to market data (orderbook updates) for tracked markets</li>
 *   <li>Provides callbacks for market data changes</li>
 *   <li>Maintains a map of tracked markets for easy access</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * public class MyEventStrategy extends EventStrategy {
 *
 *     public MyEventStrategy() {
 *         super("KXINX-25JAN10");  // Event ticker
 *     }
 *
 *     @Override
 *     protected boolean shouldTrackMarket(Market market) {
 *         // Only track active markets with sufficient volume
 *         return "active".equalsIgnoreCase(market.getStatus())
 *             && market.getVolume24h() != null
 *             && market.getVolume24h() > 1000;
 *     }
 *
 *     @Override
 *     protected void onMarketDataUpdate(ManagedMarket market) {
 *         // React to orderbook changes
 *         log.debug("Orderbook update: {} bid={} ask={}",
 *             market.getTicker(), market.getBestYesBid(), market.getBestYesAsk());
 *     }
 *
 *     @Override
 *     public void onTimer() {
 *         // Periodic strategy logic
 *         for (ManagedMarket market : getTrackedMarkets()) {
 *             evaluateMarket(market);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Run with: java ... --strategy=com.example.MyEventStrategy</p>
 */
public abstract class EventStrategy extends TradingStrategy {

    private final String eventTicker;
    private Event event;
    private String eventTitle;  // Cached for logging
    private Predicate<Market> marketFilter;
    private MarketManager.MarketChangeEvent lastChangeEvent;

    // Configuration
    private boolean failOnNoMarkets = true;  // Throw exception if no markets pass filter

    // Active state - when inactive, strategy doesn't subscribe to orderbook updates
    private volatile boolean active = false;

    // Tracked markets by ticker
    private final Map<String, Market> trackedMarkets = new ConcurrentHashMap<>();

    // Market change listener reference for cleanup
    private java.util.function.Consumer<MarketManager.MarketChangeEvent> marketChangeListener;

    /**
     * Create an EventStrategy for the specified event ticker.
     *
     * @param eventTicker The event ticker to operate on
     */
    protected EventStrategy(String eventTicker) {
        this.eventTicker = Objects.requireNonNull(eventTicker, "eventTicker must not be null");
    }

    /**
     * Create an EventStrategy with a custom market filter predicate.
     *
     * @param eventTicker The event ticker to operate on
     * @param marketFilter Predicate to filter which markets to track
     */
    protected EventStrategy(String eventTicker, Predicate<Market> marketFilter) {
        this.eventTicker = Objects.requireNonNull(eventTicker, "eventTicker must not be null");
        this.marketFilter = marketFilter;
    }

    // ==================== Event and Market Configuration ====================

    /**
     * Get the event ticker this strategy operates on.
     */
    public final String getEventTicker() {
        return eventTicker;
    }

    /**
     * Get the loaded Event.
     *
     * @return Event object, or null if not yet loaded
     */
    public final Event getEvent() {
        return event;
    }

    /**
     * Get the event title (for logging purposes).
     *
     * @return Event title, or event ticker if not yet loaded
     */
    public final String getEventTitle() {
        return eventTitle != null ? eventTitle : eventTicker;
    }

    /**
     * Get the log prefix including strategy name and event title.
     *
     * @return Log prefix like "[StrategyName|EventTitle]"
     */
    protected final String getLogPrefix() {
        return "[" + getStrategyName() + "|" + getEventTitle() + "]";
    }

    /**
     * Override to return the event title as the log identifier.
     *
     * @return Event title for logging
     */
    @Override
    protected String getLogIdentifier() {
        return getEventTitle();
    }

    /**
     * Get the expected close time for this strategy.
     * Returns the event's strike date.
     *
     * @return Event strike date, or null if not yet loaded
     */
    @Override
    public Instant getExpectedCloseTime() {
        return event != null ? event.getStrikeDate() : null;
    }

    /**
     * Set a custom market filter predicate.
     * This will be combined with {@link #shouldTrackMarket(Market)} using AND logic.
     *
     * @param filter Predicate to filter markets
     */
    protected final void setMarketFilter(Predicate<Market> filter) {
        this.marketFilter = filter;
    }

    /**
     * Configure whether to throw {@link NoMarketsTrackedException} when no markets pass the filter.
     * Default is true (fail fast).
     *
     * @param failOnNoMarkets true to throw exception, false to continue without markets
     */
    protected final void setFailOnNoMarkets(boolean failOnNoMarkets) {
        this.failOnNoMarkets = failOnNoMarkets;
    }

    /**
     * Check if the strategy will fail when no markets are tracked.
     *
     * @return true if exception will be thrown when no markets pass filter
     */
    public final boolean isFailOnNoMarkets() {
        return failOnNoMarkets;
    }

    /**
     * Check if the strategy is active (subscribed to market data).
     *
     * @return true if active
     */
    public final boolean isActive() {
        return active;
    }

    /**
     * Activate the strategy - subscribe to market orderbook updates and start the timer.
     * Has no effect if already active or if there are no tracked markets.
     */
    public final void makeActive() {
        if (active) {
            log.debug("{} Strategy already active", getLogPrefix());
            return;
        }

        if (trackedMarkets.isEmpty()) {
            log.warn("{} Cannot activate strategy - no markets tracked", getLogPrefix());
            return;
        }

        log.info("{} Activating strategy", getLogPrefix());
        active = true;

        // Subscribe to market data
        subscribeToTrackedMarkets();

        // Start the timer
        startTimerInternal();

        // Notify subclass
        onActivated();

        logActivity("Strategy activated");
    }

    /**
     * Deactivate the strategy - unsubscribe from market orderbook updates and stop the timer.
     * Has no effect if already inactive.
     */
    public final void makeInactive() {
        if (!active) {
            log.debug("{} Strategy already inactive", getLogPrefix());
            return;
        }

        log.info("{} Deactivating strategy", getLogPrefix());
        active = false;

        // Unsubscribe from market data
        unsubscribeFromTrackedMarkets();

        // Stop the timer
        stopTimerInternal();

        // Notify subclass
        onDeactivated();

        logActivity("Strategy deactivated");
    }

    /**
     * Called when the strategy is activated.
     * Override to perform setup when strategy becomes active.
     */
    protected void onActivated() {
        // Default: no-op
    }

    /**
     * Called when the strategy is deactivated.
     * Override to perform cleanup when strategy becomes inactive.
     */
    protected void onDeactivated() {
        // Default: no-op
    }

    /**
     * EventStrategy controls timer via makeActive()/makeInactive(),
     * so auto-start is disabled.
     */
    @Override
    protected boolean shouldAutoStartTimer() {
        return false;
    }

    /**
     * Get a description of the current market filter for error messages.
     * Override this method to provide a meaningful description of your filter criteria.
     *
     * @return Description of the market filter, or null if not available
     */
    protected String getMarketFilterDescription() {
        if (marketFilter != null) {
            return marketFilter.toString();
        }
        return "default (active markets only)";
    }

    // ==================== Market Filtering ====================

    /**
     * Determine if a market should be tracked by this strategy.
     * Override this method to implement custom filtering logic.
     *
     * <p>Default implementation tracks all active markets.</p>
     *
     * <p>Common filtering criteria:</p>
     * <ul>
     *   <li>Status: market.getStatus() - "active", "closed", "settled"</li>
     *   <li>Volume: market.getVolume24h() - 24h trading volume</li>
     *   <li>Liquidity: market.getLiquidityDollars() - available liquidity</li>
     *   <li>Open Interest: market.getOpenInterest() - outstanding contracts</li>
     *   <li>Expiration: market.getExpectedExpirationTime() - when market expires</li>
     *   <li>Strike: market.getFloorStrike(), market.getCapStrike() - strike prices</li>
     *   <li>Title/Subtitle: market.getTitle(), market.getSubtitle() - for keyword filtering</li>
     * </ul>
     *
     * @param market Market to evaluate
     * @return true to track the market, false to ignore it
     */
    protected boolean shouldTrackMarket(Market market) {
        // Default: track all active markets
        return "active".equalsIgnoreCase(market.getStatus());
    }

    /**
     * Internal method that combines custom filter predicate with shouldTrackMarket.
     */
    private boolean evaluateMarketFilter(Market market) {
        if (market == null) {
            return false;
        }

        // Apply custom predicate filter if set
        if (marketFilter != null && !marketFilter.test(market)) {
            return false;
        }

        // Apply shouldTrackMarket override
        return shouldTrackMarket(market);
    }

    // ==================== Tracked Markets ====================

    /**
     * Get all tracked markets.
     *
     * @return Unmodifiable collection of tracked Market objects
     */
    protected final Collection<Market> getTrackedMarketInfo() {
        return Collections.unmodifiableCollection(trackedMarkets.values());
    }

    /**
     * Get tracked market tickers.
     *
     * @return Unmodifiable set of tracked market tickers
     */
    public final Set<String> getTrackedTickers() {
        return Collections.unmodifiableSet(trackedMarkets.keySet());
    }

    /**
     * Get number of tracked markets.
     */
    public final int getTrackedMarketCount() {
        return trackedMarkets.size();
    }

    /**
     * Check if a specific market is being tracked.
     *
     * @param ticker Market ticker to check
     * @return true if the market is tracked
     */
    protected final boolean isTracking(String ticker) {
        return trackedMarkets.containsKey(ticker);
    }

    /**
     * Get static market info for a tracked market.
     *
     * @param ticker Market ticker
     * @return Market object, or null if not tracked
     */
    protected final Market getTrackedMarketInfo(String ticker) {
        return trackedMarkets.get(ticker);
    }

    /**
     * Get all tracked ManagedMarket objects (with live orderbook data).
     *
     * @return List of ManagedMarket objects for tracked markets
     */
    protected final List<ManagedMarket> getTrackedMarkets() {
        List<ManagedMarket> result = new ArrayList<>();
        MarketManager mm = getMarketManager();
        for (String ticker : trackedMarkets.keySet()) {
            ManagedMarket managed = mm.getMarket(ticker);
            if (managed != null) {
                result.add(managed);
            }
        }
        return result;
    }

    /**
     * Get a specific tracked ManagedMarket (with live orderbook data).
     *
     * @param ticker Market ticker
     * @return ManagedMarket object, or null if not tracked or not subscribed
     */
    protected final ManagedMarket getTrackedMarket(String ticker) {
        if (!trackedMarkets.containsKey(ticker)) {
            return null;
        }
        return getMarketManager().getMarket(ticker);
    }

    // ==================== Lifecycle ====================

    /**
     * Called after the strategy is fully initialized.
     * Loads the event and filters markets, but does NOT subscribe to market data.
     * The strategy starts in INACTIVE state. Call {@link #makeActive()} to subscribe
     * to market data and start the timer.
     *
     * <p>Override {@link #onEventLoaded(Event)} and {@link #onMarketsSubscribed(int)}
     * to receive notifications after these steps complete.</p>
     */
    @Override
    public final void onInitialized() {
        log.info("[{}] Initializing EventStrategy for event: {}", getStrategyName(), eventTicker);

        try {
            // Load the event with nested markets
            loadEvent();

            // Filter and track markets
            filterAndTrackMarkets();

            // NOTE: We do NOT subscribe to markets or start timer here.
            // Strategy starts in INACTIVE state. Call makeActive() to activate.

            // Notify subclass that initialization is complete (but inactive)
            onStrategyReady();

            log.info("{} Strategy initialized in INACTIVE state - call makeActive() to subscribe to markets",
                    getLogPrefix());

        } catch (NoMarketsTrackedException e) {
            // Re-throw NoMarketsTrackedException to allow caller to handle it
            // This enables StrategyManager to remove strategies with no tracked markets
            log.warn("{} No markets tracked: {}", getLogPrefix(), e.getMessage());
            onInitializationError(e);
            throw e;
        } catch (Exception e) {
            log.error("{} Failed to initialize EventStrategy: {}", getLogPrefix(), e.getMessage(), e);
            onInitializationError(e);
        }
    }

    /**
     * Load the event from the API.
     */
    private void loadEvent() {
        log.info("[{}] Loading event: {}", getStrategyName(), eventTicker);

        event = getApi().events().getEvent(eventTicker);

        if (event == null) {
            log.info("EVENT REJECTED: {} | reason: Event not found in API", eventTicker);
            throw new IllegalStateException("Event not found: " + eventTicker);
        }

        // Cache the event title for logging
        eventTitle = event.getTitle();

        int marketCount = event.getMarkets() != null ? event.getMarkets().size() : 0;

        // Log event acceptance with details
        log.info("EVENT ACCEPTED: {} ({}) | category={}, markets={}, strikeDate={}",
                eventTicker,
                eventTitle != null ? eventTitle : "no title",
                event.getCategory() != null ? event.getCategory() : "unknown",
                marketCount,
                event.getStrikeDate() != null ? event.getStrikeDate() : "not set");

        // Notify subclass
        onEventLoaded(event);
    }

    /**
     * Filter event markets and add matching ones to tracked markets.
     *
     * @throws NoMarketsTrackedException if no markets pass the filter and failOnNoMarkets is true
     */
    private void filterAndTrackMarkets() {
        List<Market> markets = event.getMarkets();
        int totalMarkets = (markets != null) ? markets.size() : 0;

        // Handle case where event has no markets
        if (markets == null || markets.isEmpty()) {
            log.info("EVENT FILTERED OUT: {} ({}) | reason: Event has no markets",
                    eventTicker, getEventTitle());
            if (failOnNoMarkets) {
                throw new NoMarketsTrackedException(eventTicker, 0, getMarketFilterDescription());
            }
            onNoMarketsTracked(0, 0);
            return;
        }

        int trackedCount = 0;

        for (Market market : markets) {
            if (evaluateMarketFilter(market)) {
                trackedMarkets.put(market.getTicker(), market);
                trackedCount++;
                log.debug("{} Tracking market: {} - {}", getLogPrefix(), market.getTicker(), market.getTitle());

                // Auto-populate display markets (up to 4)
                addDisplayMarket(market.getTicker());
            } else {
                log.debug("{} Filtered out market: {} - {} (status={})",
                        getLogPrefix(), market.getTicker(), market.getTitle(), market.getStatus());
            }
        }

        log.info("{} Tracking {} of {} markets",
                getLogPrefix(), trackedCount, totalMarkets);
        logActivity("Tracking " + trackedCount + " of " + totalMarkets + " markets");

        // Check if no markets were tracked
        if (trackedCount == 0) {
            log.info("EVENT FILTERED OUT: {} ({}) | reason: No markets passed filter | totalMarkets={}, filter={}",
                    eventTicker, getEventTitle(), totalMarkets, getMarketFilterDescription());
            if (failOnNoMarkets) {
                throw new NoMarketsTrackedException(eventTicker, totalMarkets, getMarketFilterDescription());
            }
            onNoMarketsTracked(totalMarkets, 0);
        }
    }

    /**
     * Called when no markets are tracked after filtering.
     * This is only called when failOnNoMarkets is false.
     * Override to implement custom handling for this scenario.
     *
     * @param totalMarkets   Total number of markets in the event
     * @param trackedMarkets Number of markets that passed the filter (always 0)
     */
    protected void onNoMarketsTracked(int totalMarkets, int trackedMarkets) {
        logActivityWarn("No markets tracked - " + totalMarkets + " markets were all filtered out");
    }

    /**
     * Subscribe to market data for all tracked markets.
     */
    private void subscribeToTrackedMarkets() {
        if (trackedMarkets.isEmpty()) {
            log.warn("{} No markets to subscribe to", getLogPrefix());
            return;
        }

        // Set up market change listener if not already set
        if (marketChangeListener == null) {
            marketChangeListener = this::handleMarketChange;
            getMarketManager().addMarketChangeListener(marketChangeListener);
        }

        // Subscribe to all tracked market tickers
        List<String> tickers = new ArrayList<>(trackedMarkets.keySet());
        getMarketManager().subscribe(tickers);

        log.info("{} Subscribed to {} markets", getLogPrefix(), tickers.size());
        logActivity("Subscribed to market data");

        // Notify subclass
        onMarketsSubscribed(tickers.size());
    }

    /**
     * Unsubscribe from market data for all tracked markets.
     */
    private void unsubscribeFromTrackedMarkets() {
        if (trackedMarkets.isEmpty()) {
            return;
        }

        // Unsubscribe from all tracked market tickers
        List<String> tickers = new ArrayList<>(trackedMarkets.keySet());
        getMarketManager().unsubscribe(tickers);

        // Remove market change listener
        if (marketChangeListener != null) {
            getMarketManager().removeMarketChangeListener(marketChangeListener);
            marketChangeListener = null;
        }

        log.info("{} Unsubscribed from {} markets", getLogPrefix(), tickers.size());
        logActivity("Unsubscribed from market data");

        // Notify subclass
        onMarketsUnsubscribed(tickers.size());
    }

    /**
     * Called after markets have been unsubscribed (when strategy becomes inactive).
     *
     * @param marketCount Number of markets that were unsubscribed
     */
    protected void onMarketsUnsubscribed(int marketCount) {
        // Default: no-op
    }

    /**
     * Handle market change events from MarketManager.
     */
    private void handleMarketChange(MarketManager.MarketChangeEvent event) {
        String ticker = event.getTicker();

        // Only process events for tracked markets
        if (ticker != null && !trackedMarkets.containsKey(ticker)) {
            return;
        }

        lastChangeEvent = event;

        try {
            switch (event.getType()) {
                case ORDERBOOK_SNAPSHOT:
                case ORDERBOOK_DELTA:
                    if (event.getMarket() != null) {
                        onMarketDataUpdate(event.getMarket());
                    }
                    break;

                case MARKET_INFO_UPDATED:
                    if (event.getMarket() != null) {
                        onMarketInfoUpdate(event.getMarket());
                    }
                    break;

                case CONNECTED:
                    onMarketDataConnected();
                    break;

                case DISCONNECTED:
                    onMarketDataDisconnected();
                    break;

                case ERROR:
                    onMarketDataError(ticker, event.getErrorMessage());
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.error("{} Error handling market change for {}: {}",
                    getLogPrefix(), ticker, e.getMessage(), e);
        }
    }

    /**
     * Called when the strategy is shutting down.
     * Cleans up market subscriptions and listeners.
     */
    @Override
    public void onShutdown() {
        log.info("{} Shutting down EventStrategy", getLogPrefix());

        // Deactivate if active (unsubscribes and stops timer)
        if (active) {
            makeInactive();
        }

        // Remove market change listener (in case it wasn't removed by makeInactive)
        if (marketChangeListener != null) {
            getMarketManager().removeMarketChangeListener(marketChangeListener);
            marketChangeListener = null;
        }

        trackedMarkets.clear();
    }

    // ==================== Strategy Callbacks ====================

    /**
     * Called after the event is loaded from the API.
     * Override to perform additional setup based on event data.
     *
     * @param event The loaded Event
     */
    protected void onEventLoaded(Event event) {
        // Default: no-op
    }

    /**
     * Called after markets have been filtered and subscribed.
     *
     * @param marketCount Number of markets being tracked
     */
    protected void onMarketsSubscribed(int marketCount) {
        // Default: no-op
    }

    /**
     * Called after the strategy is fully initialized and ready.
     * Override this instead of onInitialized() for custom initialization.
     */
    protected void onStrategyReady() {
        // Default: log ready status
        log.info("{} Strategy ready - tracking {} markets", getLogPrefix(), trackedMarkets.size());
    }

    /**
     * Called if initialization fails.
     *
     * @param error The error that occurred
     */
    protected void onInitializationError(Exception error) {
        // Default: log error
        log.error("{} Strategy initialization failed: {}", getLogPrefix(), error.getMessage(), error);
    }

    // ==================== Market Data Callbacks ====================

    /**
     * Called when orderbook data is updated for a tracked market.
     * This is called for both snapshots and delta updates.
     *
     * <p>Override this to react to market data changes.</p>
     *
     * @param market The managed market with updated orderbook data
     */
    protected void onMarketDataUpdate(ManagedMarket market) {
        // Default: no-op
    }

    /**
     * Called when market info (from REST API) is updated.
     *
     * @param market The managed market with updated market info
     */
    protected void onMarketInfoUpdate(ManagedMarket market) {
        // Default: no-op
    }

    /**
     * Called when the market data WebSocket connects.
     */
    protected void onMarketDataConnected() {
        logActivity("Market data connected");
    }

    /**
     * Called when the market data WebSocket disconnects.
     */
    protected void onMarketDataDisconnected() {
        logActivityWarn("Market data disconnected");
    }

    /**
     * Called when a market data error occurs.
     *
     * @param ticker The market ticker (may be null for connection errors)
     * @param errorMessage Error message
     */
    protected void onMarketDataError(String ticker, String errorMessage) {
        String msg = "Market data error" + (ticker != null ? " for " + ticker : "") + ": " + errorMessage;
        logActivityWarn(msg);
    }

    // ==================== Utility Methods ====================

    /**
     * Get the last market change event received.
     *
     * @return Last MarketChangeEvent, or null if none received
     */
    protected final MarketManager.MarketChangeEvent getLastChangeEvent() {
        return lastChangeEvent;
    }

    /**
     * Manually add a market to tracking (useful for dynamic market discovery).
     * Also subscribes to market data.
     *
     * @param market Market to add
     */
    protected final void addTrackedMarket(Market market) {
        if (market == null || market.getTicker() == null) {
            return;
        }

        String ticker = market.getTicker();
        if (!trackedMarkets.containsKey(ticker)) {
            trackedMarkets.put(ticker, market);
            getMarketManager().subscribe(ticker);
            log.info("{} Added market to tracking: {}", getLogPrefix(), ticker);
        }
    }

    /**
     * Manually remove a market from tracking.
     * Also unsubscribes from market data.
     *
     * @param ticker Market ticker to remove
     */
    protected final void removeTrackedMarket(String ticker) {
        if (trackedMarkets.remove(ticker) != null) {
            getMarketManager().unsubscribe(ticker);
            log.info("{} Removed market from tracking: {}", getLogPrefix(), ticker);
        }
    }

    /**
     * Re-evaluate all event markets against the current filter.
     * Useful if filter criteria change at runtime.
     */
    protected final void refilterMarkets() {
        if (event == null || event.getMarkets() == null) {
            return;
        }

        Set<String> currentlyTracked = new HashSet<>(trackedMarkets.keySet());
        Set<String> shouldTrack = new HashSet<>();

        for (Market market : event.getMarkets()) {
            if (evaluateMarketFilter(market)) {
                shouldTrack.add(market.getTicker());

                // Add newly matching markets
                if (!currentlyTracked.contains(market.getTicker())) {
                    addTrackedMarket(market);
                }
            }
        }

        // Remove markets that no longer match
        for (String ticker : currentlyTracked) {
            if (!shouldTrack.contains(ticker)) {
                removeTrackedMarket(ticker);
            }
        }

        log.info("{} Refiltered markets: now tracking {} markets", getLogPrefix(), trackedMarkets.size());
    }

    /**
     * Refresh event data from the API.
     * Updates the event and its markets, and re-applies filtering.
     */
    protected final void refreshEvent() {
        log.info("{} Refreshing event data", getLogPrefix());
        try {
            event = getApi().events().getEvent(eventTicker);
            if (event != null) {
                eventTitle = event.getTitle();
                onEventLoaded(event);
                refilterMarkets();
            }
        } catch (Exception e) {
            log.error("{} Failed to refresh event: {}", getLogPrefix(), e.getMessage(), e);
        }
    }
}
