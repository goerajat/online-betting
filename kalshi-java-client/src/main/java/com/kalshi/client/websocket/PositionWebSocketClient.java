package com.kalshi.client.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.model.Position;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for subscribing to Kalshi market position updates.
 *
 * <p>This client connects to the Kalshi WebSocket API and subscribes to
 * the market_positions channel. Authentication is required.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PositionWebSocketClient client = PositionWebSocketClient.builder()
 *     .authenticator(authenticator)
 *     .build();
 *
 * client.subscribe(new PositionUpdateConsumer() {
 *     @Override
 *     public void onPositionUpdate(Position position) {
 *         System.out.println("Position update: " + position);
 *     }
 * });
 *
 * // Later, to disconnect:
 * client.close();
 * }</pre>
 */
public class PositionWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(PositionWebSocketClient.class);

    public static final String DEFAULT_WS_URL = "wss://api.elections.kalshi.com/trade-api/ws/v2";
    public static final String DEMO_WS_URL = "wss://demo-api.kalshi.co/trade-api/ws/v2";

    private static final String CHANNEL_NAME = "market_positions";

    private final String wsUrl;
    private final KalshiAuthenticator authenticator;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicInteger messageIdCounter;
    private final AtomicBoolean connected;
    private final List<PositionUpdateConsumer> consumers;

    private WebSocket webSocket;
    private int subscriptionId;

    private PositionWebSocketClient(Builder builder) {
        this.wsUrl = builder.wsUrl;
        this.authenticator = builder.authenticator;
        this.httpClient = builder.httpClient;
        this.objectMapper = createObjectMapper();
        this.messageIdCounter = new AtomicInteger(1);
        this.connected = new AtomicBoolean(false);
        this.consumers = new CopyOnWriteArrayList<>();

        if (authenticator == null) {
            throw new IllegalArgumentException("Authenticator is required for position updates");
        }
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Subscribe to position updates for all markets.
     *
     * @param consumer The consumer to receive updates
     */
    public void subscribe(PositionUpdateConsumer consumer) {
        subscribe(null, consumer);
    }

    /**
     * Subscribe to position updates for specific market tickers.
     *
     * @param marketTickers Optional list of market tickers to filter (null for all)
     * @param consumer The consumer to receive updates
     */
    public void subscribe(List<String> marketTickers, PositionUpdateConsumer consumer) {
        consumers.add(consumer);

        if (!connected.get()) {
            connect(marketTickers, consumer);
        } else {
            sendSubscribeCommand(marketTickers);
        }
    }

    /**
     * Connect to the WebSocket.
     */
    private void connect(List<String> marketTickers, PositionUpdateConsumer consumer) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(wsUrl);

        // Add authentication headers
        String path = "/trade-api/ws/v2";
        KalshiAuthenticator.AuthHeaders authHeaders = authenticator.generateHeaders("GET", path);
        requestBuilder.header("KALSHI-ACCESS-KEY", authHeaders.getAccessKey());
        requestBuilder.header("KALSHI-ACCESS-TIMESTAMP", authHeaders.getTimestamp());
        requestBuilder.header("KALSHI-ACCESS-SIGNATURE", authHeaders.getSignature());

        Request request = requestBuilder.build();

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("Position WebSocket connected to {}", wsUrl);
                connected.set(true);
                notifyConnected();
                sendSubscribeCommand(marketTickers);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("Position WebSocket closing: {} - {}", code, reason);
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("Position WebSocket closed: {} - {}", code, reason);
                connected.set(false);
                notifyDisconnected(code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("Position WebSocket failure", t);
                connected.set(false);
                notifyError(t);
            }
        });
    }

    /**
     * Send a subscribe command.
     */
    private void sendSubscribeCommand(List<String> marketTickers) {
        Map<String, Object> subscribeMessage = new HashMap<>();
        subscribeMessage.put("id", messageIdCounter.getAndIncrement());
        subscribeMessage.put("cmd", "subscribe");

        Map<String, Object> params = new HashMap<>();
        params.put("channels", List.of(CHANNEL_NAME));
        if (marketTickers != null && !marketTickers.isEmpty()) {
            params.put("market_tickers", marketTickers);
        }
        subscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(subscribeMessage);
            logger.debug("Sending position subscribe: {}", json);
            webSocket.send(json);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize subscribe message", e);
        }
    }

    /**
     * Handle an incoming WebSocket message.
     */
    private void handleMessage(String text) {
        logger.debug("Received position message: {}", text);

        try {
            JsonNode root = objectMapper.readTree(text);

            // Check message type
            String type = root.has("type") ? root.get("type").asText() : null;

            if ("market_position".equals(type)) {
                JsonNode msgNode = root.get("msg");
                if (msgNode != null) {
                    Position position = objectMapper.treeToValue(msgNode, Position.class);
                    notifyPositionUpdate(position);
                }
            } else if ("subscribed".equals(type)) {
                subscriptionId = root.has("sid") ? root.get("sid").asInt() : 0;
                logger.info("Position subscription confirmed, sid={}", subscriptionId);
            } else if ("error".equals(type)) {
                String errorMsg = root.has("msg") ? root.get("msg").asText() : "Unknown error";
                logger.error("Position WebSocket error: {}", errorMsg);
                notifyError(new RuntimeException(errorMsg));
            }
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse position message: {}", text, e);
        }
    }

    private void notifyConnected() {
        for (PositionUpdateConsumer consumer : consumers) {
            try {
                consumer.onConnected();
            } catch (Exception e) {
                logger.error("Error in position consumer onConnected", e);
            }
        }
    }

    private void notifyPositionUpdate(Position position) {
        for (PositionUpdateConsumer consumer : consumers) {
            try {
                consumer.onPositionUpdate(position);
            } catch (Exception e) {
                logger.error("Error in position consumer for {}", position.getMarketTicker(), e);
            }
        }
    }

    private void notifyDisconnected(int code, String reason) {
        for (PositionUpdateConsumer consumer : consumers) {
            try {
                consumer.onDisconnected(code, reason);
            } catch (Exception e) {
                logger.error("Error in position consumer onDisconnected", e);
            }
        }
    }

    private void notifyError(Throwable error) {
        for (PositionUpdateConsumer consumer : consumers) {
            try {
                consumer.onError(error);
            } catch (Exception e) {
                logger.error("Error in position consumer onError", e);
            }
        }
    }

    /**
     * Unsubscribe from position updates.
     */
    public void unsubscribe() {
        if (!connected.get() || webSocket == null) {
            return;
        }

        Map<String, Object> unsubscribeMessage = new HashMap<>();
        unsubscribeMessage.put("id", messageIdCounter.getAndIncrement());
        unsubscribeMessage.put("cmd", "unsubscribe");

        Map<String, Object> params = new HashMap<>();
        params.put("sids", List.of(subscriptionId));
        unsubscribeMessage.put("params", params);

        try {
            String json = objectMapper.writeValueAsString(unsubscribeMessage);
            logger.debug("Sending position unsubscribe: {}", json);
            webSocket.send(json);
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
        consumers.clear();
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for PositionWebSocketClient.
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
         * Set the authenticator (required for position updates).
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
        public PositionWebSocketClient build() {
            if (httpClient == null) {
                httpClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .pingInterval(30, TimeUnit.SECONDS)
                        .build();
            }
            return new PositionWebSocketClient(this);
        }
    }
}
