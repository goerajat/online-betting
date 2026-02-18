package com.kalshi.client.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.model.MarketLifecycleAction;
import com.kalshi.client.model.MarketLifecycleEvent;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for subscribing to Kalshi market lifecycle events.
 *
 * <p>This client connects to the Kalshi WebSocket API and subscribes to
 * the market_lifecycle_v2 channel for specified market tickers.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * MarketLifecycleWebSocketClient client = MarketLifecycleWebSocketClient.builder()
 *     .useDemo()
 *     .authenticator(authenticator)
 *     .build();
 *
 * client.addListener(new MarketLifecycleListener() {
 *     @Override
 *     public void onMarketDetermined(String ticker, Market market, String result) {
 *         System.out.println("Market " + ticker + " determined: " + result);
 *     }
 * });
 *
 * client.subscribe("TICKER-123");
 *
 * // Later, to disconnect:
 * client.close();
 * }</pre>
 */
public class MarketLifecycleWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(MarketLifecycleWebSocketClient.class);

    public static final String DEFAULT_WS_URL = "wss://api.elections.kalshi.com/trade-api/ws/v2";
    public static final String DEMO_WS_URL = "wss://demo-api.kalshi.co/trade-api/ws/v2";
    private static final String CHANNEL_NAME = "market_lifecycle_v2";

    private final String wsUrl;
    private final KalshiAuthenticator authenticator;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter;
    private final AtomicBoolean connected;
    private final Set<String> subscribedTickers;
    private final List<MarketLifecycleListener> listeners;

    private WebSocket webSocket;

    private MarketLifecycleWebSocketClient(Builder builder) {
        this.wsUrl = builder.wsUrl;
        this.authenticator = builder.authenticator;
        this.httpClient = builder.httpClient;
        this.objectMapper = createObjectMapper();
        this.messageIdCounter = new AtomicInteger(1);
        this.connected = new AtomicBoolean(false);
        this.subscribedTickers = Collections.synchronizedSet(new HashSet<>());
        this.listeners = new CopyOnWriteArrayList<>();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /**
     * Add a listener for lifecycle events.
     *
     * @param listener The listener to add
     */
    public void addListener(MarketLifecycleListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(MarketLifecycleListener listener) {
        listeners.remove(listener);
    }

    /**
     * Subscribe to lifecycle events for a single market ticker.
     *
     * @param marketTicker The market ticker to subscribe to
     */
    public void subscribe(String marketTicker) {
        subscribe(List.of(marketTicker));
    }

    /**
     * Subscribe to lifecycle events for multiple market tickers.
     *
     * @param marketTickers The market tickers to subscribe to
     */
    public void subscribe(List<String> marketTickers) {
        List<String> newTickers = new ArrayList<>();
        for (String ticker : marketTickers) {
            if (subscribedTickers.add(ticker)) {
                newTickers.add(ticker);
            }
        }

        if (newTickers.isEmpty()) {
            return;
        }

        if (!connected.get()) {
            connect();
        }

        if (connected.get()) {
            sendSubscribeCommand(newTickers);
        }
    }

    /**
     * Connect to the WebSocket.
     */
    private void connect() {
        Request.Builder requestBuilder = new Request.Builder()
                .url(wsUrl);

        // Add authentication headers if authenticator is available
        if (authenticator != null) {
            String path = "/trade-api/ws/v2";
            KalshiAuthenticator.AuthHeaders authHeaders = authenticator.generateHeaders("GET", path);
            requestBuilder.header("KALSHI-ACCESS-KEY", authHeaders.getAccessKey());
            requestBuilder.header("KALSHI-ACCESS-TIMESTAMP", authHeaders.getTimestamp());
            requestBuilder.header("KALSHI-ACCESS-SIGNATURE", authHeaders.getSignature());
        }

        Request request = requestBuilder.build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("Market lifecycle WebSocket connected to {}", wsUrl);
                connected.set(true);
                notifyConnected();

                // Subscribe to all pending tickers
                if (!subscribedTickers.isEmpty()) {
                    sendSubscribeCommand(new ArrayList<>(subscribedTickers));
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("Market lifecycle WebSocket closing: {} - {}", code, reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("Market lifecycle WebSocket closed: {} - {}", code, reason);
                connected.set(false);
                notifyDisconnected(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("Market lifecycle WebSocket failure", t);
                connected.set(false);
                notifyError(t);
            }
        });
    }

    /**
     * Send a subscribe command for the specified market tickers.
     */
    private void sendSubscribeCommand(List<String> marketTickers) {
        Map<String, Object> subscribeMessage = new HashMap<>();
        subscribeMessage.put("id", messageIdCounter.getAndIncrement());
        subscribeMessage.put("cmd", "subscribe");

        Map<String, Object> params = new HashMap<>();
        params.put("channels", List.of(CHANNEL_NAME));
        params.put("market_tickers", marketTickers);
        subscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(subscribeMessage);
            logger.debug("Sending lifecycle subscribe: {}", json);
            webSocket.send(json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize subscribe message", e);
        }
    }

    /**
     * Handle an incoming WebSocket message.
     */
    private void handleMessage(String text) {
        logger.debug("Received lifecycle message: {}", text);

        try {
            JsonNode root = objectMapper.readTree(text);

            String type = root.has("type") ? root.get("type").asText() : null;

            if ("subscribed".equals(type)) {
                logger.info("Lifecycle subscription confirmed");
                return;
            }

            if (root.has("error")) {
                logger.error("WebSocket error: {}", root.get("error"));
                return;
            }

            // Check if this is a lifecycle event
            if (type != null && type.startsWith("market_lifecycle")) {
                JsonNode msg = root.get("msg");
                if (msg != null) {
                    MarketLifecycleEvent event = objectMapper.treeToValue(msg, MarketLifecycleEvent.class);
                    dispatchEvent(event);
                }
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse lifecycle message: {}", text, e);
        }
    }

    /**
     * Dispatch a lifecycle event to all listeners.
     */
    private void dispatchEvent(MarketLifecycleEvent event) {
        String ticker = event.getMarketTicker();
        MarketLifecycleAction action = event.getAction();

        if (action == null) {
            logger.warn("Unknown lifecycle action for event: {}", event);
            return;
        }

        logger.info("Market lifecycle event: {} - {}", ticker, action);

        for (MarketLifecycleListener listener : listeners) {
            try {
                // Always call the generic handler first
                listener.onLifecycleEvent(event);

                // Then call the specific handler based on action
                switch (action) {
                    case CREATED:
                        listener.onMarketCreated(ticker, event.getMarket());
                        break;
                    case ACTIVATED:
                        listener.onMarketActivated(ticker, event.getMarket());
                        break;
                    case DEACTIVATED:
                        listener.onMarketDeactivated(ticker, event.getMarket());
                        break;
                    case CLOSE_DATE_UPDATED:
                        listener.onMarketCloseDateUpdated(ticker, event.getMarket(), event.getCloseTime());
                        break;
                    case DETERMINED:
                        listener.onMarketDetermined(ticker, event.getMarket(), event.getResult());
                        break;
                    case SETTLED:
                        listener.onMarketSettled(ticker, event.getMarket());
                        break;
                }
            } catch (Exception e) {
                logger.error("Error in lifecycle listener for {}", ticker, e);
            }
        }
    }

    /**
     * Unsubscribe from a market ticker.
     *
     * @param marketTicker The market ticker to unsubscribe from
     */
    public void unsubscribe(String marketTicker) {
        unsubscribe(List.of(marketTicker));
    }

    /**
     * Unsubscribe from multiple market tickers.
     *
     * @param marketTickers The market tickers to unsubscribe from
     */
    public void unsubscribe(List<String> marketTickers) {
        if (!connected.get() || webSocket == null) {
            marketTickers.forEach(subscribedTickers::remove);
            return;
        }

        Map<String, Object> unsubscribeMessage = new HashMap<>();
        unsubscribeMessage.put("id", messageIdCounter.getAndIncrement());
        unsubscribeMessage.put("cmd", "unsubscribe");

        Map<String, Object> params = new HashMap<>();
        params.put("channels", List.of(CHANNEL_NAME));
        params.put("market_tickers", marketTickers);
        unsubscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(unsubscribeMessage);
            logger.debug("Sending lifecycle unsubscribe: {}", json);
            webSocket.send(json);

            marketTickers.forEach(subscribedTickers::remove);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize unsubscribe message", e);
        }
    }

    /**
     * Get the set of currently subscribed tickers.
     */
    public Set<String> getSubscribedTickers() {
        return Collections.unmodifiableSet(subscribedTickers);
    }

    /**
     * Check if the WebSocket is connected.
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Close the WebSocket connection.
     */
    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "Client closing");
        }
        connected.set(false);
        subscribedTickers.clear();
    }

    // Notification helpers

    private void notifyConnected() {
        for (MarketLifecycleListener listener : listeners) {
            try {
                listener.onConnected();
            } catch (Exception e) {
                logger.error("Error in onConnected listener", e);
            }
        }
    }

    private void notifyDisconnected(int code, String reason) {
        for (MarketLifecycleListener listener : listeners) {
            try {
                listener.onDisconnected(code, reason);
            } catch (Exception e) {
                logger.error("Error in onDisconnected listener", e);
            }
        }
    }

    private void notifyError(Throwable error) {
        for (MarketLifecycleListener listener : listeners) {
            try {
                listener.onError(error);
            } catch (Exception e) {
                logger.error("Error in onError listener", e);
            }
        }
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for MarketLifecycleWebSocketClient.
     */
    public static class Builder {
        private String wsUrl = DEFAULT_WS_URL;
        private KalshiAuthenticator authenticator;
        private OkHttpClient httpClient;

        /**
         * Set the WebSocket URL.
         */
        public Builder wsUrl(String wsUrl) {
            this.wsUrl = wsUrl;
            return this;
        }

        /**
         * Use the demo/sandbox environment.
         */
        public Builder useDemo() {
            this.wsUrl = DEMO_WS_URL;
            return this;
        }

        /**
         * Set the authenticator for authenticated connections.
         */
        public Builder authenticator(KalshiAuthenticator authenticator) {
            this.authenticator = authenticator;
            return this;
        }

        /**
         * Set a custom OkHttpClient.
         */
        public Builder httpClient(OkHttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Build the WebSocket client.
         */
        public MarketLifecycleWebSocketClient build() {
            if (httpClient == null) {
                httpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS)
                        .build();
            }
            return new MarketLifecycleWebSocketClient(this);
        }
    }
}
