package com.kalshi.client.strategy;

import com.betting.marketdata.api.MarketDataManager;
import com.kalshi.client.KalshiApi;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for trading strategies.
 * Provides access to order service, position manager, and market manager.
 * Receives callbacks for order and position changes.
 * Includes a timer service that invokes onTimer() at regular intervals.
 *
 * <p>Note: Risk checks are configured and enforced by the application,
 * not by individual strategies. Strategies cannot enable or disable risk checks.</p>
 *
 * <p>Usage:</p>
 * <ol>
 *   <li>Extend this class and implement the callback methods</li>
 *   <li>Specify your strategy class name via command line parameter: --strategy=com.example.MyStrategy</li>
 *   <li>The strategy will be initialized when the app starts</li>
 *   <li>onInitialized() is called after all services are ready</li>
 *   <li>onTimer() is called every 5 seconds (configurable via getTimerIntervalSeconds())</li>
 * </ol>
 *
 * <p>Example:</p>
 * <pre>{@code
 * public class MyStrategy extends TradingStrategy {
 *     @Override
 *     public void onInitialized() {
 *         // Subscribe to markets, set up initial state
 *         getMarketManager().subscribe("TICKER-123");
 *     }
 *
 *     @Override
 *     public void onTimer() {
 *         // Called every 5 seconds - check conditions, update state, etc.
 *         log.info("Timer tick - checking market conditions...");
 *     }
 *
 *     @Override
 *     public void onOrderCreated(Order order) {
 *         log.info("Order created: {}", order.getOrderId());
 *     }
 *
 *     @Override
 *     public void onPositionUpdated(Position position) {
 *         // React to position changes
 *     }
 * }
 * }</pre>
 */
public abstract class TradingStrategy {

    /**
     * Logger for subclasses to use directly.
     * Using the subclass logger provides accurate class names and line numbers in log output.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final long DEFAULT_TIMER_INTERVAL_SECONDS = 5;
    private static final int MAX_DISPLAY_MARKETS = 4;

    private KalshiApi api;
    private OrderManager orderManager;
    private PositionManager positionManager;
    private MarketManager marketManager;
    private MarketDataManager marketDataManager;  // External market data (E*TRADE, etc.)
    private volatile boolean initialized = false;

    // Timer service
    private ScheduledExecutorService timerExecutor;
    private ScheduledFuture<?> timerFuture;
    private volatile boolean timerRunning = false;

    // Activity log for strategy-specific logging
    private final StrategyActivityLog activityLog = new DefaultStrategyActivityLog();

    // Strategy timing
    private Instant openTime;

    // Display markets for UI orderbook display (up to 4)
    private final Set<String> displayMarketTickers = Collections.synchronizedSet(new LinkedHashSet<>());

    // Market data label for real-time underlying data display
    private volatile String marketDataLabelText = "";
    private final List<MarketDataLabelListener> marketDataLabelListeners = new CopyOnWriteArrayList<>();

    /**
     * Initialize the strategy with required services.
     * Called by the application before onInitialized().
     *
     * @param api The Kalshi API instance
     * @param orderManager Order manager for tracking open orders
     * @param positionManager Position manager for tracking positions
     * @param marketManager Market manager for orderbook data
     */
    public final void initialize(KalshiApi api, OrderManager orderManager,
                                  PositionManager positionManager, MarketManager marketManager) {
        initialize(api, orderManager, positionManager, marketManager, null);
    }

    /**
     * Initialize the strategy with required services including external market data.
     * Called by the application before onInitialized().
     *
     * @param api The Kalshi API instance
     * @param orderManager Order manager for tracking open orders
     * @param positionManager Position manager for tracking positions
     * @param marketManager Market manager for orderbook data
     * @param marketDataManager External market data manager (E*TRADE, etc.) - can be null
     */
    public final void initialize(KalshiApi api, OrderManager orderManager,
                                  PositionManager positionManager, MarketManager marketManager,
                                  MarketDataManager marketDataManager) {
        this.api = api;
        this.orderManager = orderManager;
        this.positionManager = positionManager;
        this.marketManager = marketManager;
        this.marketDataManager = marketDataManager;
        this.openTime = Instant.now();
        this.initialized = true;

        log.info("[{}] Strategy initialized (marketDataManager={})", getLogIdentifier(),
                marketDataManager != null ? marketDataManager.getProviderName() : "none");
        activityLog.info("Strategy initialized");

        // Call lifecycle callback
        onInitialized();

        // Start the timer service (after onInitialized completes)
        // Note: For EventStrategy, timer is controlled by makeActive()/makeInactive()
        if (shouldAutoStartTimer()) {
            startTimer();
        }
    }

    /**
     * Override to control automatic timer start after initialization.
     * Default is true. EventStrategy overrides this to return false
     * since it controls the timer via makeActive()/makeInactive().
     *
     * @return true to auto-start timer after initialization
     */
    protected boolean shouldAutoStartTimer() {
        return true;
    }

    /**
     * Check if the strategy has been initialized.
     */
    public final boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the strategy name (simple class name).
     */
    public final String getStrategyName() {
        return getClass().getSimpleName();
    }

    /**
     * Get the log identifier for this strategy.
     * Override in subclasses to provide a more descriptive identifier.
     * Default is the simple class name.
     *
     * @return Log identifier string
     */
    protected String getLogIdentifier() {
        return getClass().getSimpleName();
    }

    // ==================== Activity Log ====================

    /**
     * Get the strategy's activity log.
     * Use this to display strategy-specific log messages in the UI.
     *
     * @return The activity log
     */
    public final StrategyActivityLog getActivityLog() {
        return activityLog;
    }

    /**
     * Log an activity message (also logs to the activity log for UI display).
     *
     * @param message The message to log
     */
    protected void logActivity(String message) {
        log.info("[{}] {}", getLogIdentifier(), message);
        activityLog.info(message);
    }

    /**
     * Log an activity warning.
     *
     * @param message The message to log
     */
    protected void logActivityWarn(String message) {
        log.warn("[{}] {}", getLogIdentifier(), message);
        activityLog.warn(message);
    }

    /**
     * Log an activity error.
     *
     * @param message The message to log
     * @param error The error
     */
    protected void logActivityError(String message, Throwable error) {
        log.error("[{}] {}: {}", getLogIdentifier(), message, error.getMessage(), error);
        activityLog.error(message, error);
    }

    /**
     * Log a trade event (order/fill).
     *
     * @param message The trade message
     */
    protected void logTrade(String message) {
        log.info("[{}] TRADE: {}", getLogIdentifier(), message);
        activityLog.trade(message);
    }

    // ==================== Strategy Timing ====================

    /**
     * Get the time when this strategy was initialized.
     *
     * @return Open time, or null if not yet initialized
     */
    public final Instant getOpenTime() {
        return openTime;
    }

    /**
     * Get the expected close time for this strategy.
     * Override in subclasses to provide strategy-specific close time.
     *
     * @return Expected close time, or null if unknown
     */
    public Instant getExpectedCloseTime() {
        return null;
    }

    // ==================== Display Markets ====================

    /**
     * Get the market tickers to display in the UI orderbook panel.
     * Returns up to 4 market tickers for orderbook display.
     *
     * @return Unmodifiable set of market tickers to display
     */
    public Set<String> getDisplayMarketTickers() {
        return Collections.unmodifiableSet(displayMarketTickers);
    }

    /**
     * Set the markets to display in the UI.
     * Only the first 4 markets will be retained.
     *
     * @param tickers Market tickers to display
     */
    protected void setDisplayMarkets(String... tickers) {
        displayMarketTickers.clear();
        for (String ticker : tickers) {
            if (ticker != null && !ticker.isEmpty()) {
                displayMarketTickers.add(ticker);
                if (displayMarketTickers.size() >= MAX_DISPLAY_MARKETS) {
                    break;
                }
            }
        }
    }

    /**
     * Add a market to the display list.
     *
     * @param ticker Market ticker to add
     * @return true if added, false if at capacity
     */
    protected boolean addDisplayMarket(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return false;
        }
        if (displayMarketTickers.size() >= MAX_DISPLAY_MARKETS) {
            return false;
        }
        return displayMarketTickers.add(ticker);
    }

    /**
     * Remove a market from the display list.
     *
     * @param ticker Market ticker to remove
     */
    protected void removeDisplayMarket(String ticker) {
        displayMarketTickers.remove(ticker);
    }

    /**
     * Clear all display markets.
     */
    protected void clearDisplayMarkets() {
        displayMarketTickers.clear();
    }

    // ==================== Market Data Label ====================

    /**
     * Listener interface for market data label updates.
     * The UI can implement this to receive real-time label updates.
     */
    public interface MarketDataLabelListener {
        /**
         * Called when the market data label text is updated.
         * Note: This may be called from a background thread.
         * The listener is responsible for ensuring UI updates are on the correct thread.
         *
         * @param labelText The new label text
         */
        void onMarketDataLabelUpdate(String labelText);
    }

    /**
     * Set the market data label text.
     * This is typically called when the strategy receives market data updates
     * to display real-time information about the underlying (e.g., "SPY: $583.25 +0.5%").
     *
     * <p>This method is thread-safe and can be called from any thread.</p>
     *
     * @param labelText The text to display
     */
    protected void setMarketDataLabel(String labelText) {
        this.marketDataLabelText = labelText != null ? labelText : "";
        notifyMarketDataLabelListeners();
    }

    /**
     * Get the current market data label text.
     *
     * @return The current label text
     */
    public String getMarketDataLabelText() {
        return marketDataLabelText;
    }

    /**
     * Add a listener for market data label updates.
     *
     * @param listener The listener to add
     */
    public void addMarketDataLabelListener(MarketDataLabelListener listener) {
        if (listener != null) {
            marketDataLabelListeners.add(listener);
        }
    }

    /**
     * Remove a market data label listener.
     *
     * @param listener The listener to remove
     */
    public void removeMarketDataLabelListener(MarketDataLabelListener listener) {
        marketDataLabelListeners.remove(listener);
    }

    /**
     * Notify all listeners of a market data label update.
     */
    private void notifyMarketDataLabelListeners() {
        String text = marketDataLabelText;
        for (MarketDataLabelListener listener : marketDataLabelListeners) {
            try {
                listener.onMarketDataLabelUpdate(text);
            } catch (Exception e) {
                log.error("Error notifying market data label listener: {}", e.getMessage());
            }
        }
    }

    // ==================== Timer Service ====================

    /**
     * Get the timer interval in seconds.
     * Override this method to customize the timer interval.
     * Default is 5 seconds.
     *
     * @return Timer interval in seconds
     */
    protected long getTimerIntervalSeconds() {
        return DEFAULT_TIMER_INTERVAL_SECONDS;
    }

    /**
     * Check if the timer should be enabled.
     * Override this method to disable the timer.
     * Default is true.
     *
     * @return true if timer should be enabled
     */
    protected boolean isTimerEnabled() {
        return true;
    }

    /**
     * Start the timer service.
     */
    private void startTimer() {
        startTimerInternal();
    }

    /**
     * Internal method to start the timer, callable by subclasses.
     * Used by EventStrategy to start timer on activation.
     */
    protected final void startTimerInternal() {
        if (timerRunning) {
            log.debug("[{}] Timer already running", getLogIdentifier());
            return;
        }

        if (!isTimerEnabled()) {
            log.info("[{}] Timer disabled", getLogIdentifier());
            return;
        }

        long intervalSeconds = getTimerIntervalSeconds();
        if (intervalSeconds <= 0) {
            log.warn("Invalid timer interval: {}s, timer disabled", intervalSeconds);
            return;
        }

        timerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Strategy-Timer-" + getLogIdentifier());
            t.setDaemon(true);
            return t;
        });

        timerFuture = timerExecutor.scheduleAtFixedRate(
                this::executeTimerCallback,
                intervalSeconds,  // initial delay
                intervalSeconds,  // period
                TimeUnit.SECONDS
        );

        timerRunning = true;
        log.info("[{}] Timer started with {}s interval", getLogIdentifier(), intervalSeconds);
    }

    /**
     * Stop the timer service.
     */
    private void stopTimer() {
        stopTimerInternal();
    }

    /**
     * Internal method to stop the timer, callable by subclasses.
     * Used by EventStrategy to stop timer on deactivation.
     */
    protected final void stopTimerInternal() {
        if (!timerRunning) {
            log.debug("[{}] Timer not running", getLogIdentifier());
            return;
        }

        timerRunning = false;

        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }

        if (timerExecutor != null) {
            timerExecutor.shutdown();
            try {
                if (!timerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    timerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                timerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            timerExecutor = null;
        }

        log.info("[{}] Timer stopped", getLogIdentifier());
    }

    /**
     * Execute the timer callback safely.
     */
    private void executeTimerCallback() {
        if (!initialized || !timerRunning) {
            return;
        }

        try {
            onTimer();
        } catch (Exception e) {
            log.error("[{}] Error in onTimer callback: {}", getLogIdentifier(), e.getMessage(), e);
        }
    }

    /**
     * Check if the timer is currently running.
     *
     * @return true if timer is running
     */
    public final boolean isTimerRunning() {
        return timerRunning;
    }

    // ==================== Service Accessors ====================

    /**
     * Get the Kalshi API instance.
     */
    protected KalshiApi getApi() {
        return api;
    }

    /**
     * Get the order service for creating/canceling orders.
     */
    protected OrderService getOrderService() {
        return api.orders();
    }

    /**
     * Get the order manager for querying open orders.
     */
    protected OrderManager getOrderManager() {
        return orderManager;
    }

    /**
     * Get the position manager for querying positions.
     */
    protected PositionManager getPositionManager() {
        return positionManager;
    }

    /**
     * Get the market manager for orderbook data.
     */
    protected MarketManager getMarketManager() {
        return marketManager;
    }

    /**
     * Get the external market data manager (E*TRADE, etc.).
     * Returns null if no external market data is configured.
     *
     * @return MarketDataManager instance or null
     */
    protected MarketDataManager getMarketDataManager() {
        return marketDataManager;
    }

    /**
     * Check if external market data is available.
     *
     * @return true if MarketDataManager is configured and authenticated
     */
    protected boolean hasMarketData() {
        return marketDataManager != null && marketDataManager.isAuthenticated();
    }

    // ==================== Order Helpers ====================

    /**
     * Send a limit order.
     *
     * @param ticker Market ticker
     * @param side "yes" or "no"
     * @param action "buy" or "sell"
     * @param quantity Number of contracts
     * @param priceInCents Price in cents (1-99)
     * @return Created Order object
     */
    protected Order sendOrder(String ticker, String side, String action, int quantity, int priceInCents) {
        log.info("Sending order: {} {} {} {} @ {}¢", action, quantity, side, ticker, priceInCents);

        boolean isYes = "yes".equalsIgnoreCase(side);
        boolean isBuy = "buy".equalsIgnoreCase(action);

        if (isBuy && isYes) {
            return getOrderService().buyYes(ticker, quantity, priceInCents);
        } else if (isBuy) {
            return getOrderService().buyNo(ticker, quantity, priceInCents);
        } else if (isYes) {
            return getOrderService().sellYes(ticker, quantity, priceInCents);
        } else {
            return getOrderService().sellNo(ticker, quantity, priceInCents);
        }
    }

    /**
     * Send a buy order.
     *
     * @param ticker Market ticker
     * @param side "yes" or "no"
     * @param quantity Number of contracts
     * @param priceInCents Price in cents (1-99)
     * @return Created Order object
     */
    protected Order buy(String ticker, String side, int quantity, int priceInCents) {
        return sendOrder(ticker, side, "buy", quantity, priceInCents);
    }

    /**
     * Send a sell order.
     *
     * @param ticker Market ticker
     * @param side "yes" or "no"
     * @param quantity Number of contracts
     * @param priceInCents Price in cents (1-99)
     * @return Created Order object
     */
    protected Order sell(String ticker, String side, int quantity, int priceInCents) {
        return sendOrder(ticker, side, "sell", quantity, priceInCents);
    }

    /**
     * Cancel an order by ID.
     *
     * @param orderId Order ID to cancel
     * @return Canceled Order object
     */
    protected Order cancelOrder(String orderId) {
        log.info("Canceling order: {}", orderId);
        return getOrderService().cancelOrder(orderId);
    }

    // ==================== Lifecycle Callbacks ====================

    /**
     * Called after the strategy is fully initialized and all services are ready.
     * Override this to perform setup like subscribing to markets.
     */
    public void onInitialized() {
        // Default: no-op
    }

    /**
     * Called when the application is shutting down.
     * Override this to perform cleanup.
     * Note: The timer is automatically stopped before this is called.
     */
    public void onShutdown() {
        // Default: no-op
    }

    /**
     * Internal shutdown method called by the application.
     * Stops the timer and then calls onShutdown().
     */
    public final void shutdown() {
        log.info("[{}] Shutting down strategy", getLogIdentifier());
        stopTimer();
        try {
            onShutdown();
        } catch (Exception e) {
            log.error("[{}] Error in onShutdown callback: {}", getLogIdentifier(), e.getMessage(), e);
        }
    }

    // ==================== Timer Callback ====================

    /**
     * Called at regular intervals (default: every 5 seconds).
     * Override this to perform periodic tasks like:
     * - Checking market conditions
     * - Updating strategy state
     * - Monitoring positions
     * - Sending heartbeat orders
     *
     * <p>Note: This method is called from a background thread.
     * Ensure thread-safety when accessing shared state.</p>
     */
    public void onTimer() {
        // Default: no-op
    }

    // ==================== Order Callbacks ====================

    /**
     * Called when a new order is created/detected.
     *
     * @param order The created order
     */
    public void onOrderCreated(Order order) {
        // Default: no-op
    }

    /**
     * Called when an existing order is modified (price or quantity change).
     *
     * @param order The modified order
     */
    public void onOrderModified(Order order) {
        // Default: no-op
    }

    /**
     * Called when an order is canceled or fully filled.
     *
     * @param order The removed order
     */
    public void onOrderRemoved(Order order) {
        // Default: no-op
    }

    // ==================== Position Callbacks ====================

    /**
     * Called when a new position is opened.
     *
     * @param position The new position
     */
    public void onPositionOpened(Position position) {
        // Default: no-op
    }

    /**
     * Called when a position is updated (size or cost change).
     *
     * @param position The updated position
     */
    public void onPositionUpdated(Position position) {
        // Default: no-op
    }

    /**
     * Called when a position is closed (now flat).
     *
     * @param position The closed position
     */
    public void onPositionClosed(Position position) {
        // Default: no-op
    }

}
