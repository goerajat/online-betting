package com.kalshi.client.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.client.auth.KalshiAuthenticator;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for subscribing to Kalshi orderbook updates.
 *
 * <p>This client connects to the Kalshi WebSocket API and subscribes to
 * the orderbook_delta channel for specified market tickers. It invokes
 * the provided consumer when snapshots and delta updates are received.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * OrderbookWebSocketClient client = OrderbookWebSocketClient.builder()
 *     .useDemo()
 *     .authenticator(authenticator)  // Optional for public data
 *     .build();
 *
 * client.subscribe("TICKER-123", new OrderbookUpdateConsumer() {
 *     @Override
 *     public void onSnapshot(OrderbookSnapshot snapshot) {
 *         System.out.println("Received snapshot: " + snapshot);
 *     }
 *
 *     @Override
 *     public void onDelta(OrderbookDelta delta) {
 *         System.out.println("Received delta: " + delta);
 *     }
 * });
 *
 * // Later, to disconnect:
 * client.close();
 * }</pre>
 */
public class OrderbookWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(OrderbookWebSocketClient.class);

    public static final String DEFAULT_WS_URL = "wss://api.elections.kalshi.com/trade-api/ws/v2";
    public static final String DEMO_WS_URL = "wss://demo-api.kalshi.co/trade-api/ws/v2";

    private final String wsUrl;
    private final KalshiAuthenticator authenticator;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter;
    private final AtomicBoolean connected;
    private final Map<String, OrderbookUpdateConsumer> subscriptions;

    private WebSocket webSocket;
    private String currentMarketTicker;

    private OrderbookWebSocketClient(Builder builder) {
        this.wsUrl = builder.wsUrl;
        this.authenticator = builder.authenticator;
        this.httpClient = builder.httpClient;
        this.objectMapper = createObjectMapper();
        this.messageIdCounter = new AtomicInteger(1);
        this.connected = new AtomicBoolean(false);
        this.subscriptions = new HashMap<>();
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Subscribe to orderbook updates for a single market ticker.
     *
     * @param marketTicker The market ticker to subscribe to
     * @param consumer The consumer to receive updates
     */
    public void subscribe(String marketTicker, OrderbookUpdateConsumer consumer) {
        subscribe(List.of(marketTicker), consumer);
    }

    /**
     * Subscribe to orderbook updates for multiple market tickers.
     *
     * @param marketTickers The market tickers to subscribe to
     * @param consumer The consumer to receive updates
     */
    public void subscribe(List<String> marketTickers, OrderbookUpdateConsumer consumer) {
        // Store subscription
        for (String ticker : marketTickers) {
            subscriptions.put(ticker, consumer);
        }

        if (!connected.get()) {
            connect(marketTickers, consumer);
        } else {
            sendSubscribeCommand(marketTickers);
        }
    }

    /**
     * Connect to the WebSocket and subscribe to the specified tickers.
     */
    private void connect(List<String> marketTickers, OrderbookUpdateConsumer consumer) {
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
                logger.info("WebSocket connected to {}", wsUrl);
                connected.set(true);
                consumer.onConnected();
                sendSubscribeCommand(marketTickers);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket closing: {} - {}", code, reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket closed: {} - {}", code, reason);
                connected.set(false);
                consumer.onDisconnected(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket failure", t);
                connected.set(false);
                consumer.onError(t);
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
        params.put("channels", List.of("orderbook_delta"));
        params.put("market_tickers", marketTickers);
        subscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(subscribeMessage);
            logger.debug("Sending subscribe: {}", json);
            webSocket.send(json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize subscribe message", e);
        }
    }

    /**
     * Handle an incoming WebSocket message.
     */
    private void handleMessage(String text) {
        logger.debug("Received message: {}", text);

        try {
            WebSocketMessage message = objectMapper.readValue(text, WebSocketMessage.class);

            if (message.isSnapshot() && message.getMsg() != null) {
                OrderbookSnapshot snapshot = objectMapper.treeToValue(
                        message.getMsg(), OrderbookSnapshot.class);
                notifySnapshot(snapshot);
            } else if (message.isDelta() && message.getMsg() != null) {
                OrderbookDelta delta = objectMapper.treeToValue(
                        message.getMsg(), OrderbookDelta.class);
                notifyDelta(delta);
            } else if (message.isSubscribed()) {
                logger.info("Subscription confirmed: {}", message);
            } else if (message.isError()) {
                logger.error("WebSocket error message: {}", message.getError());
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse WebSocket message: {}", text, e);
        }
    }

    /**
     * Notify all relevant consumers of a snapshot.
     */
    private void notifySnapshot(OrderbookSnapshot snapshot) {
        String ticker = snapshot.getMarketTicker();
        OrderbookUpdateConsumer consumer = subscriptions.get(ticker);
        if (consumer != null) {
            try {
                consumer.onSnapshot(snapshot);
            } catch (Exception e) {
                logger.error("Error in snapshot consumer for {}", ticker, e);
            }
        }
        // Also notify consumers that subscribed without a specific ticker
        subscriptions.forEach((key, cons) -> {
            if (!key.equals(ticker)) {
                // Check if this is a wildcard subscription or the message matches
            }
        });
    }

    /**
     * Notify all relevant consumers of a delta.
     */
    private void notifyDelta(OrderbookDelta delta) {
        String ticker = delta.getMarketTicker();
        OrderbookUpdateConsumer consumer = subscriptions.get(ticker);
        if (consumer != null) {
            try {
                consumer.onDelta(delta);
            } catch (Exception e) {
                logger.error("Error in delta consumer for {}", ticker, e);
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
            return;
        }

        Map<String, Object> unsubscribeMessage = new HashMap<>();
        unsubscribeMessage.put("id", messageIdCounter.getAndIncrement());
        unsubscribeMessage.put("cmd", "unsubscribe");

        Map<String, Object> params = new HashMap<>();
        params.put("channels", List.of("orderbook_delta"));
        params.put("market_tickers", marketTickers);
        unsubscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(unsubscribeMessage);
            logger.debug("Sending unsubscribe: {}", json);
            webSocket.send(json);

            // Remove from subscriptions
            for (String ticker : marketTickers) {
                subscriptions.remove(ticker);
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize unsubscribe message", e);
        }
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
        subscriptions.clear();
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OrderbookWebSocketClient.
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
        public OrderbookWebSocketClient build() {
            if (httpClient == null) {
                httpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS)
                        .build();
            }
            return new OrderbookWebSocketClient(this);
        }
    }
}
