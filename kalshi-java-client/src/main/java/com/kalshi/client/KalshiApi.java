package com.kalshi.client;

import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.cache.DataCache;
import com.kalshi.client.manager.EventManager;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Series;
import com.kalshi.client.service.EventService;
import com.kalshi.client.service.MarketService;
import com.kalshi.client.service.OrderService;
import com.kalshi.client.service.SeriesService;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Main entry point for the Kalshi API client.
 * Provides access to all API services: series, events, markets, and orders.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * KalshiApi api = KalshiApi.builder()
 *     .credentials("your-api-key-id", privateKeyPem)
 *     .build();
 *
 * // Get market data
 * List<Market> markets = api.markets().getMarkets();
 * Market market = api.markets().getMarket("TICKER");
 * Orderbook orderbook = api.markets().getOrderbook("TICKER");
 *
 * // Create an order
 * Order order = api.orders().buyYes("TICKER", 10, 50);
 *
 * // Cancel an order
 * api.orders().cancelOrder(order.getOrderId());
 * }</pre>
 */
public class KalshiApi {

    private final KalshiClient client;
    private final KalshiAuthenticator authenticator;
    private final boolean isDemo;

    // Services (stateless, created once)
    private final SeriesService seriesService;
    private final EventService eventService;
    private final MarketService marketService;
    private final OrderService orderService;

    // Centralized data cache
    private final DataCache cache;

    // Singleton managers (lazy initialized, thread-safe)
    private volatile OrderManager orderManager;
    private volatile PositionManager positionManager;
    private volatile MarketManager marketManager;
    private volatile EventManager eventManager;
    private final Object managerLock = new Object();

    private KalshiApi(KalshiClient client, KalshiAuthenticator authenticator, boolean isDemo, DataCache cache) {
        this.client = client;
        this.authenticator = authenticator;
        this.isDemo = isDemo;
        this.cache = cache != null ? cache : new DataCache();
        this.seriesService = new SeriesService(client);
        this.eventService = new EventService(client);
        this.marketService = new MarketService(client);
        this.orderService = new OrderService(client);
    }

    /**
     * Get the series service for retrieving series data.
     */
    public SeriesService series() {
        return seriesService;
    }

    /**
     * Get the event service for retrieving event data.
     */
    public EventService events() {
        return eventService;
    }

    /**
     * Get the market service for retrieving market and orderbook data.
     */
    public MarketService markets() {
        return marketService;
    }

    /**
     * Get the order service for creating, canceling, and amending orders.
     */
    public OrderService orders() {
        return orderService;
    }

    /**
     * Get the data cache for cached access to series, events, and markets.
     */
    public DataCache cache() {
        return cache;
    }

    /**
     * Get the underlying HTTP client.
     */
    public KalshiClient getClient() {
        return client;
    }

    // ==================== Cached Data Access ====================

    /**
     * Get a series, using cache if available.
     *
     * @param ticker Series ticker
     * @return The Series (from cache or fetched)
     */
    public Series getSeriesCached(String ticker) {
        return cache.getSeries(ticker, () -> seriesService.getSeries(ticker));
    }

    /**
     * Get an event, using cache if available.
     *
     * @param ticker Event ticker
     * @return The Event (from cache or fetched, without nested markets)
     */
    public Event getEventCached(String ticker) {
        return cache.getEvent(ticker, () -> eventService.getEvent(ticker));
    }

    /**
     * Get a market, using cache if available.
     *
     * @param ticker Market ticker
     * @return The Market (from cache or fetched)
     */
    public Market getMarketCached(String ticker) {
        return cache.getMarket(ticker, () -> marketService.getMarket(ticker));
    }

    /**
     * Get all events for a series, fetching and caching them.
     *
     * @param seriesTicker Series ticker
     * @return List of Events (cached after first fetch, without nested markets)
     */
    public List<Event> getEventsBySeriesCached(String seriesTicker) {
        // First check if we have cached events for this series
        List<Event> cached = cache.getEventsBySeriesCached(seriesTicker);
        if (!cached.isEmpty()) {
            return cached;
        }

        // Fetch all events for series and cache them
        List<Event> events = eventService.getAllEvents(
                EventService.EventQuery.builder()
                        .seriesTicker(seriesTicker)
                        .withNestedMarkets(false)
                        .build()
        );
        cache.putAllEvents(events);
        return events;
    }

    /**
     * Get all markets for an event, fetching and caching them.
     *
     * @param eventTicker Event ticker
     * @return List of Markets (cached after first fetch)
     */
    public List<Market> getMarketsByEventCached(String eventTicker) {
        // First check if we have cached markets for this event
        List<Market> cached = cache.getMarketsByEventCached(eventTicker);
        if (!cached.isEmpty()) {
            return cached;
        }

        // Fetch markets directly from MarketService and cache them
        List<Market> markets = marketService.getAllMarketsByEvent(eventTicker);
        cache.putAllMarkets(markets);
        return markets;
    }

    /**
     * Create a new builder for KalshiApi.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for KalshiApi.
     */
    public static class Builder {
        private String baseUrl = KalshiClient.DEFAULT_BASE_URL;
        private KalshiAuthenticator authenticator;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(30);
        private DataCache cache;

        /**
         * Set the base URL (default is production URL).
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Use the demo/sandbox environment.
         */
        public Builder useDemo() {
            this.baseUrl = KalshiClient.DEMO_BASE_URL;
            return this;
        }

        /**
         * Set authentication using API key ID and private key PEM string.
         *
         * @param apiKeyId API key ID
         * @param privateKeyPem Private key in PEM format
         */
        public Builder credentials(String apiKeyId, String privateKeyPem) {
            this.authenticator = KalshiAuthenticator.fromPem(apiKeyId, privateKeyPem);
            return this;
        }

        /**
         * Set authentication using API key ID and private key file.
         *
         * @param apiKeyId API key ID
         * @param privateKeyPath Path to the private key PEM file
         */
        public Builder credentialsFromFile(String apiKeyId, Path privateKeyPath) {
            this.authenticator = KalshiAuthenticator.fromFile(apiKeyId, privateKeyPath);
            return this;
        }

        /**
         * Set authentication using a pre-built authenticator.
         */
        public Builder authenticator(KalshiAuthenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        /**
         * Set connect timeout.
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout.
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set write timeout.
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * Set a custom data cache. If not set, a default cache will be created.
         *
         * @param cache DataCache instance to use
         */
        public Builder cache(DataCache cache) {
            this.cache = cache;
            return this;
        }

        /**
         * Build the KalshiApi instance.
         * Note: Authentication is optional for public endpoints (series, events, markets).
         * It is required for authenticated endpoints (orders, portfolio).
         */
        public KalshiApi build() {
            KalshiClient.Builder clientBuilder = KalshiClient.builder()
                    .baseUrl(baseUrl)
                    .connectTimeout(connectTimeout)
                    .readTimeout(readTimeout)
                    .writeTimeout(writeTimeout);

            if (authenticator != null) {
                clientBuilder.authenticator(authenticator);
            }

            boolean isDemo = KalshiClient.DEMO_BASE_URL.equals(baseUrl);
            return new KalshiApi(clientBuilder.build(), authenticator, isDemo, cache);
        }
    }

    /**
     * Check if the API has authentication configured.
     */
    public boolean hasAuthentication() {
        return client.hasAuthentication();
    }

    // ==================== Singleton Managers ====================

    /**
     * Get the shared OrderManager instance (singleton).
     * Creates a new instance on first call with default 5-second polling interval.
     * The OrderManager polls for open orders and maintains them organized by ticker.
     *
     * @return Shared OrderManager instance (not started - call start() to begin polling)
     */
    public OrderManager getOrderManager() {
        if (orderManager == null) {
            synchronized (managerLock) {
                if (orderManager == null) {
                    orderManager = new OrderManager(this);
                }
            }
        }
        return orderManager;
    }

    /**
     * Get the shared PositionManager instance (singleton).
     * Creates a new instance on first call.
     * Requires authentication to be configured.
     *
     * @return Shared PositionManager instance (not started - call start() to begin)
     * @throws IllegalStateException if authentication is not configured
     */
    public PositionManager getPositionManager() {
        if (positionManager == null) {
            synchronized (managerLock) {
                if (positionManager == null) {
                    if (authenticator == null) {
                        throw new IllegalStateException("Authentication is required for PositionManager");
                    }
                    positionManager = new PositionManager(authenticator, isDemo);
                }
            }
        }
        return positionManager;
    }

    /**
     * Get the shared MarketManager instance (singleton).
     * Creates a new instance on first call.
     * Combines REST API market info with real-time WebSocket orderbook updates.
     *
     * @return Shared MarketManager instance
     */
    public MarketManager getMarketManager() {
        if (marketManager == null) {
            synchronized (managerLock) {
                if (marketManager == null) {
                    marketManager = new MarketManager(this);
                }
            }
        }
        return marketManager;
    }

    /**
     * Get the shared EventManager instance (singleton).
     * Creates a new instance on first call.
     * Provides cached access to events with async loading capabilities.
     *
     * @return Shared EventManager instance
     */
    public EventManager getEventManager() {
        if (eventManager == null) {
            synchronized (managerLock) {
                if (eventManager == null) {
                    eventManager = new EventManager(this);
                }
            }
        }
        return eventManager;
    }

    /**
     * Get the authenticator used by this API instance.
     *
     * @return The authenticator or null if not configured
     */
    public KalshiAuthenticator getAuthenticator() {
        return authenticator;
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown all singleton managers and clear the cache.
     */
    public void shutdown() {
        synchronized (managerLock) {
            if (orderManager != null) {
                orderManager.shutdown();
                orderManager = null;
            }
            if (positionManager != null) {
                positionManager.stop();
                positionManager = null;
            }
            if (marketManager != null) {
                marketManager.shutdown();
                marketManager = null;
            }
            if (eventManager != null) {
                eventManager.shutdown();
                eventManager = null;
            }
        }
        cache.invalidateAll();
    }
}
