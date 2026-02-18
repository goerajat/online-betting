package com.kalshi.client.manager;

import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.model.Position;
import com.kalshi.client.websocket.PositionUpdateConsumer;
import com.kalshi.client.websocket.PositionWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages market positions by subscribing to the Kalshi WebSocket API.
 * Maintains positions organized by market ticker and provides change notifications.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * PositionManager manager = new PositionManager(authenticator);
 *
 * // Add listener for position changes
 * manager.addPositionChangeListener(event -> {
 *     System.out.println("Position change: " + event.getType() + " - " + event.getPosition());
 * });
 *
 * // Start receiving updates
 * manager.start();
 *
 * // Query positions
 * Position pos = manager.getPosition("TICKER-123");
 * List<Position> allPositions = manager.getAllPositions();
 * int totalContracts = manager.getTotalPositionSize();
 *
 * // Stop when done
 * manager.stop();
 * }</pre>
 */
public class PositionManager {

    private static final Logger log = LoggerFactory.getLogger(PositionManager.class);

    private final KalshiAuthenticator authenticator;
    private final boolean useDemo;
    private final ConcurrentHashMap<String, Position> positionsByTicker;
    private final List<Consumer<PositionChangeEvent>> listeners;

    private PositionWebSocketClient wsClient;
    private volatile boolean running;

    /**
     * Create a PositionManager for production environment.
     *
     * @param authenticator Kalshi authenticator (required)
     */
    public PositionManager(KalshiAuthenticator authenticator) {
        this(authenticator, false);
    }

    /**
     * Create a PositionManager.
     *
     * @param authenticator Kalshi authenticator (required)
     * @param useDemo Use demo environment if true
     */
    public PositionManager(KalshiAuthenticator authenticator, boolean useDemo) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.useDemo = useDemo;
        this.positionsByTicker = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Start receiving position updates via WebSocket.
     */
    public synchronized void start() {
        if (running) {
            log.warn("PositionManager is already running");
            return;
        }

        log.info("Starting PositionManager");

        PositionWebSocketClient.Builder builder = PositionWebSocketClient.builder()
                .authenticator(authenticator);

        if (useDemo) {
            builder.useDemo();
        }

        wsClient = builder.build();

        wsClient.subscribe(new PositionUpdateConsumer() {
            @Override
            public void onConnected() {
                log.info("PositionManager connected to WebSocket");
                notifyListeners(new PositionChangeEvent(PositionChangeType.CONNECTED, null));
            }

            @Override
            public void onPositionUpdate(Position position) {
                handlePositionUpdate(position);
            }

            @Override
            public void onDisconnected(int code, String reason) {
                log.info("PositionManager disconnected: {} - {}", code, reason);
                notifyListeners(new PositionChangeEvent(PositionChangeType.DISCONNECTED, null));
            }

            @Override
            public void onError(Throwable error) {
                log.error("PositionManager error: {}", error.getMessage(), error);
                notifyListeners(new PositionChangeEvent(PositionChangeType.ERROR, null, error.getMessage()));
            }
        });

        running = true;
    }

    /**
     * Start receiving position updates for specific tickers only.
     *
     * @param tickers List of market tickers to subscribe to
     */
    public synchronized void start(List<String> tickers) {
        if (running) {
            log.warn("PositionManager is already running");
            return;
        }

        log.info("Starting PositionManager for tickers: {}", tickers);

        PositionWebSocketClient.Builder builder = PositionWebSocketClient.builder()
                .authenticator(authenticator);

        if (useDemo) {
            builder.useDemo();
        }

        wsClient = builder.build();

        wsClient.subscribe(tickers, new PositionUpdateConsumer() {
            @Override
            public void onConnected() {
                log.info("PositionManager connected to WebSocket");
                notifyListeners(new PositionChangeEvent(PositionChangeType.CONNECTED, null));
            }

            @Override
            public void onPositionUpdate(Position position) {
                handlePositionUpdate(position);
            }

            @Override
            public void onDisconnected(int code, String reason) {
                log.info("PositionManager disconnected: {} - {}", code, reason);
                notifyListeners(new PositionChangeEvent(PositionChangeType.DISCONNECTED, null));
            }

            @Override
            public void onError(Throwable error) {
                log.error("PositionManager error: {}", error.getMessage(), error);
                notifyListeners(new PositionChangeEvent(PositionChangeType.ERROR, null, error.getMessage()));
            }
        });

        running = true;
    }

    /**
     * Stop receiving position updates.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        log.info("Stopping PositionManager");

        if (wsClient != null) {
            wsClient.close();
            wsClient = null;
        }

        running = false;
    }

    /**
     * Check if the manager is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Check if connected to WebSocket.
     */
    public boolean isConnected() {
        return wsClient != null && wsClient.isConnected();
    }

    private void handlePositionUpdate(Position position) {
        String ticker = position.getMarketTicker();
        Position oldPosition = positionsByTicker.get(ticker);

        PositionChangeType changeType;
        if (oldPosition == null) {
            changeType = PositionChangeType.ADDED;
        } else if (position.isFlat()) {
            changeType = PositionChangeType.CLOSED;
        } else {
            changeType = PositionChangeType.UPDATED;
        }

        // Update position (or remove if flat)
        if (position.isFlat()) {
            positionsByTicker.remove(ticker);
        } else {
            positionsByTicker.put(ticker, position);
        }

        log.debug("Position {} for {}: {}", changeType, ticker, position);
        notifyListeners(new PositionChangeEvent(changeType, position));
    }

    // ==================== Query Methods ====================

    /**
     * Get position for a specific ticker.
     *
     * @param ticker Market ticker
     * @return Position or null if no position
     */
    public Position getPosition(String ticker) {
        return positionsByTicker.get(ticker);
    }

    /**
     * Get all current positions.
     *
     * @return Unmodifiable list of all positions
     */
    public List<Position> getAllPositions() {
        return Collections.unmodifiableList(new ArrayList<>(positionsByTicker.values()));
    }

    /**
     * Get all tickers with open positions.
     *
     * @return Set of tickers
     */
    public Set<String> getPositionTickers() {
        return Collections.unmodifiableSet(new HashSet<>(positionsByTicker.keySet()));
    }

    /**
     * Get number of markets with open positions.
     */
    public int getPositionCount() {
        return positionsByTicker.size();
    }

    /**
     * Check if there's a position for a ticker.
     */
    public boolean hasPosition(String ticker) {
        return positionsByTicker.containsKey(ticker);
    }

    /**
     * Get total position size across all markets.
     */
    public int getTotalPositionSize() {
        return positionsByTicker.values().stream()
                .mapToInt(Position::getAbsoluteSize)
                .sum();
    }

    /**
     * Get total realized P&L across all positions in dollars.
     */
    public BigDecimal getTotalRealizedPnl() {
        return positionsByTicker.values().stream()
                .map(Position::getRealizedPnlDollars)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total fees paid across all positions in dollars.
     */
    public BigDecimal getTotalFeesPaid() {
        return positionsByTicker.values().stream()
                .map(Position::getFeesPaidDollars)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get total volume traded across all positions.
     */
    public int getTotalVolume() {
        return positionsByTicker.values().stream()
                .mapToInt(p -> p.getVolume() != null ? p.getVolume() : 0)
                .sum();
    }

    /**
     * Get all long positions.
     */
    public List<Position> getLongPositions() {
        return positionsByTicker.values().stream()
                .filter(Position::isLong)
                .toList();
    }

    /**
     * Get all short positions.
     */
    public List<Position> getShortPositions() {
        return positionsByTicker.values().stream()
                .filter(Position::isShort)
                .toList();
    }

    // ==================== Listeners ====================

    /**
     * Add a listener for position changes.
     *
     * @param listener Consumer to receive PositionChangeEvents
     */
    public void addPositionChangeListener(Consumer<PositionChangeEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Remove a position change listener.
     *
     * @param listener Listener to remove
     */
    public void removePositionChangeListener(Consumer<PositionChangeEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(PositionChangeEvent event) {
        for (Consumer<PositionChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in position change listener: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== Event Types ====================

    /**
     * Types of position changes.
     */
    public enum PositionChangeType {
        CONNECTED,    // WebSocket connected
        DISCONNECTED, // WebSocket disconnected
        ADDED,        // New position opened
        UPDATED,      // Position size/cost changed
        CLOSED,       // Position closed (now flat)
        ERROR         // Error occurred
    }

    /**
     * Event representing a position change.
     */
    public static class PositionChangeEvent {
        private final PositionChangeType type;
        private final Position position;
        private final String errorMessage;

        public PositionChangeEvent(PositionChangeType type, Position position) {
            this(type, position, null);
        }

        public PositionChangeEvent(PositionChangeType type, Position position, String errorMessage) {
            this.type = type;
            this.position = position;
            this.errorMessage = errorMessage;
        }

        public PositionChangeType getType() {
            return type;
        }

        public Position getPosition() {
            return position;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        @Override
        public String toString() {
            if (type == PositionChangeType.ERROR) {
                return "PositionChangeEvent{type=ERROR, message='" + errorMessage + "'}";
            }
            if (type == PositionChangeType.CONNECTED || type == PositionChangeType.DISCONNECTED) {
                return "PositionChangeEvent{type=" + type + "}";
            }
            return "PositionChangeEvent{type=" + type + ", position=" + position + "}";
        }
    }
}
