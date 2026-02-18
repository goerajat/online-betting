package com.kalshi.client.manager;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.model.Market;
import com.kalshi.client.websocket.OrderbookDelta;
import com.kalshi.client.websocket.OrderbookSnapshot;
import com.kalshi.client.websocket.OrderbookUpdateConsumer;
import com.kalshi.client.websocket.OrderbookWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages market data and orderbooks for subscribed market tickers.
 * Combines REST API market information with real-time WebSocket orderbook updates.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * MarketManager manager = new MarketManager(kalshiApi);
 *
 * // Add listener for market changes
 * manager.addMarketChangeListener(event -> {
 *     System.out.println("Market change: " + event.getType() + " - " + event.getTicker());
 * });
 *
 * // Subscribe to markets
 * manager.subscribe("TICKER-123");
 * manager.subscribe(List.of("TICKER-456", "TICKER-789"));
 *
 * // Query market data
 * ManagedMarket market = manager.getMarket("TICKER-123");
 * Integer bestBid = market.getBestYesBid();
 * List<ManagedMarket.PriceLevel> bids = market.getYesBids(5);
 *
 * // Unsubscribe when done
 * manager.unsubscribe("TICKER-123");
 *
 * // Shutdown
 * manager.shutdown();
 * }</pre>
 */
public class MarketManager {

    private static final Logger log = LoggerFactory.getLogger(MarketManager.class);

    private final KalshiApi api;
    private final KalshiAuthenticator authenticator;
    private final boolean useDemo;
    private final ExecutorService executor;

    // Managed markets by ticker
    private final ConcurrentHashMap<String, ManagedMarket> markets = new ConcurrentHashMap<>();

    // WebSocket client for orderbook updates
    private OrderbookWebSocketClient wsClient;
    private volatile boolean wsConnected = false;

    // Listeners
    private final List<Consumer<MarketChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Create a MarketManager.
     *
     * @param api KalshiApi instance for REST API calls
     */
    public MarketManager(KalshiApi api) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.authenticator = api.getAuthenticator();
        this.useDemo = !api.getClient().getBaseUrl().contains("api.elections.kalshi.com");
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MarketManager-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Subscription Management ====================

    /**
     * Subscribe to a market ticker.
     * Fetches market info via REST and subscribes to orderbook updates via WebSocket.
     *
     * @param ticker Market ticker to subscribe to
     */
    public void subscribe(String ticker) {
        subscribe(List.of(ticker));
    }

    /**
     * Subscribe to multiple market tickers.
     *
     * @param tickers List of market tickers to subscribe to
     */
    public void subscribe(List<String> tickers) {
        List<String> newTickers = new ArrayList<>();

        for (String ticker : tickers) {
            if (!markets.containsKey(ticker)) {
                ManagedMarket managedMarket = new ManagedMarket(ticker);
                markets.put(ticker, managedMarket);
                newTickers.add(ticker);
                log.info("Subscribing to market: {}", ticker);
            }
        }

        if (newTickers.isEmpty()) {
            return;
        }

        // Fetch market info in background
        for (String ticker : newTickers) {
            executor.submit(() -> fetchMarketInfo(ticker));
        }

        // Subscribe to WebSocket orderbook updates
        ensureWebSocketConnected(newTickers);
    }

    /**
     * Unsubscribe from a market ticker.
     *
     * @param ticker Market ticker to unsubscribe from
     */
    public void unsubscribe(String ticker) {
        unsubscribe(List.of(ticker));
    }

    /**
     * Unsubscribe from multiple market tickers.
     *
     * @param tickers List of market tickers to unsubscribe from
     */
    public void unsubscribe(List<String> tickers) {
        for (String ticker : tickers) {
            ManagedMarket removed = markets.remove(ticker);
            if (removed != null) {
                log.info("Unsubscribed from market: {}", ticker);
                notifyListeners(new MarketChangeEvent(MarketChangeType.UNSUBSCRIBED, ticker, removed));
            }
        }

        // Unsubscribe from WebSocket
        if (wsClient != null && wsClient.isConnected()) {
            wsClient.unsubscribe(tickers);
        }
    }

    /**
     * Unsubscribe from all markets.
     */
    public void unsubscribeAll() {
        List<String> allTickers = new ArrayList<>(markets.keySet());
        unsubscribe(allTickers);
    }

    /**
     * Check if subscribed to a ticker.
     */
    public boolean isSubscribed(String ticker) {
        return markets.containsKey(ticker);
    }

    /**
     * Get all subscribed tickers.
     */
    public Set<String> getSubscribedTickers() {
        return Collections.unmodifiableSet(new HashSet<>(markets.keySet()));
    }

    /**
     * Get count of subscribed markets.
     */
    public int getSubscriptionCount() {
        return markets.size();
    }

    // ==================== Market Data Access ====================

    /**
     * Get a managed market by ticker.
     *
     * @param ticker Market ticker
     * @return ManagedMarket or null if not subscribed
     */
    public ManagedMarket getMarket(String ticker) {
        return markets.get(ticker);
    }

    /**
     * Get all managed markets.
     *
     * @return Unmodifiable collection of managed markets
     */
    public Collection<ManagedMarket> getAllMarkets() {
        return Collections.unmodifiableCollection(markets.values());
    }

    /**
     * Refresh market info from REST API.
     *
     * @param ticker Market ticker to refresh
     */
    public void refreshMarketInfo(String ticker) {
        if (markets.containsKey(ticker)) {
            executor.submit(() -> fetchMarketInfo(ticker));
        }
    }

    /**
     * Refresh market info for all subscribed markets.
     */
    public void refreshAllMarketInfo() {
        for (String ticker : markets.keySet()) {
            executor.submit(() -> fetchMarketInfo(ticker));
        }
    }

    // ==================== WebSocket Management ====================

    private void ensureWebSocketConnected(List<String> tickers) {
        if (wsClient == null) {
            createWebSocketClient();
        }

        if (!wsConnected) {
            // Will subscribe after connection
            wsClient.subscribe(tickers, createOrderbookConsumer());
        } else {
            // Already connected, just subscribe to new tickers
            wsClient.subscribe(tickers, createOrderbookConsumer());
        }
    }

    private void createWebSocketClient() {
        OrderbookWebSocketClient.Builder builder = OrderbookWebSocketClient.builder();

        if (useDemo) {
            builder.useDemo();
        }

        if (authenticator != null) {
            builder.authenticator(authenticator);
        }

        wsClient = builder.build();
    }

    private OrderbookUpdateConsumer createOrderbookConsumer() {
        return new OrderbookUpdateConsumer() {
            @Override
            public void onConnected() {
                wsConnected = true;
                log.info("MarketManager WebSocket connected");
                notifyListeners(new MarketChangeEvent(MarketChangeType.CONNECTED, null, null));
            }

            @Override
            public void onSnapshot(OrderbookSnapshot snapshot) {
                handleSnapshot(snapshot);
            }

            @Override
            public void onDelta(OrderbookDelta delta) {
                handleDelta(delta);
            }

            @Override
            public void onDisconnected(int code, String reason) {
                wsConnected = false;
                log.info("MarketManager WebSocket disconnected: {} - {}", code, reason);
                notifyListeners(new MarketChangeEvent(MarketChangeType.DISCONNECTED, null, null));
            }

            @Override
            public void onError(Throwable error) {
                log.error("MarketManager WebSocket error: {}", error.getMessage(), error);
                notifyListeners(new MarketChangeEvent(MarketChangeType.ERROR, null, null, error.getMessage()));
            }
        };
    }

    private void handleSnapshot(OrderbookSnapshot snapshot) {
        String ticker = snapshot.getMarketTicker();
        ManagedMarket managedMarket = markets.get(ticker);

        if (managedMarket != null) {
            managedMarket.applySnapshot(snapshot.getYes(), snapshot.getNo());
            log.debug("Applied orderbook snapshot for {}", ticker);
            notifyListeners(new MarketChangeEvent(MarketChangeType.ORDERBOOK_SNAPSHOT, ticker, managedMarket));
        }
    }

    private void handleDelta(OrderbookDelta delta) {
        String ticker = delta.getMarketTicker();
        ManagedMarket managedMarket = markets.get(ticker);

        if (managedMarket != null) {
            managedMarket.applyDelta(delta.isYesSide(), delta.getPrice(), delta.getDelta());
            notifyListeners(new MarketChangeEvent(MarketChangeType.ORDERBOOK_DELTA, ticker, managedMarket));
        }
    }

    private void fetchMarketInfo(String ticker) {
        try {
            Market market = api.markets().getMarket(ticker);
            ManagedMarket managedMarket = markets.get(ticker);

            if (managedMarket != null && market != null) {
                managedMarket.setMarket(market);
                log.debug("Fetched market info for {}: {}", ticker, market.getTitle());
                notifyListeners(new MarketChangeEvent(MarketChangeType.MARKET_INFO_UPDATED, ticker, managedMarket));
            }
        } catch (Exception e) {
            log.error("Failed to fetch market info for {}: {}", ticker, e.getMessage());
            notifyListeners(new MarketChangeEvent(MarketChangeType.ERROR, ticker, null, e.getMessage()));
        }
    }

    // ==================== Listeners ====================

    /**
     * Add a listener for market changes.
     *
     * @param listener Consumer to receive MarketChangeEvents
     */
    public void addMarketChangeListener(Consumer<MarketChangeEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Remove a market change listener.
     *
     * @param listener Listener to remove
     */
    public void removeMarketChangeListener(Consumer<MarketChangeEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(MarketChangeEvent event) {
        for (Consumer<MarketChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in market change listener: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Check if WebSocket is connected.
     */
    public boolean isConnected() {
        return wsConnected && wsClient != null && wsClient.isConnected();
    }

    /**
     * Shutdown the MarketManager and release resources.
     */
    public void shutdown() {
        log.info("Shutting down MarketManager");

        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }

        wsConnected = false;
        markets.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Event Types ====================

    /**
     * Types of market changes.
     */
    public enum MarketChangeType {
        CONNECTED,           // WebSocket connected
        DISCONNECTED,        // WebSocket disconnected
        MARKET_INFO_UPDATED, // Market info fetched/updated from REST API
        ORDERBOOK_SNAPSHOT,  // Orderbook snapshot received
        ORDERBOOK_DELTA,     // Orderbook delta update received
        UNSUBSCRIBED,        // Unsubscribed from a market
        ERROR                // Error occurred
    }

    /**
     * Event representing a market change.
     */
    public static class MarketChangeEvent {
        private final MarketChangeType type;
        private final String ticker;
        private final ManagedMarket market;
        private final String errorMessage;

        public MarketChangeEvent(MarketChangeType type, String ticker, ManagedMarket market) {
            this(type, ticker, market, null);
        }

        public MarketChangeEvent(MarketChangeType type, String ticker, ManagedMarket market, String errorMessage) {
            this.type = type;
            this.ticker = ticker;
            this.market = market;
            this.errorMessage = errorMessage;
        }

        public MarketChangeType getType() {
            return type;
        }

        public String getTicker() {
            return ticker;
        }

        public ManagedMarket getMarket() {
            return market;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (type == MarketChangeType.ERROR) {
                return "MarketChangeEvent{type=ERROR, ticker='" + ticker + "', message='" + errorMessage + "'}";
            }
            if (type == MarketChangeType.CONNECTED || type == MarketChangeType.DISCONNECTED) {
                return "MarketChangeEvent{type=" + type + "}";
            }
            return "MarketChangeEvent{type=" + type + ", ticker='" + ticker + "'}";
        }
    }
}
