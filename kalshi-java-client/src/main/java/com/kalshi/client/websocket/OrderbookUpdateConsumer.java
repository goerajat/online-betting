package com.kalshi.client.websocket;

/**
 * Consumer interface for receiving orderbook updates via WebSocket.
 * Implement this interface to handle orderbook snapshots and delta updates.
 */
public interface OrderbookUpdateConsumer {

    /**
     * Called when an orderbook snapshot is received.
     * This is the initial full state of the orderbook after subscribing.
     *
     * @param snapshot The complete orderbook snapshot
     */
    void onSnapshot(OrderbookSnapshot snapshot);

    /**
     * Called when an orderbook delta (update) is received.
     * This represents an incremental change to the orderbook.
     *
     * @param delta The orderbook delta update
     */
    void onDelta(OrderbookDelta delta);

    /**
     * Called when the WebSocket connection is established.
     * Default implementation does nothing.
     */
    default void onConnected() {
    }

    /**
     * Called when the WebSocket connection is closed.
     * Default implementation does nothing.
     *
     * @param code The close code
     * @param reason The close reason
     */
    default void onDisconnected(int code, String reason) {
    }

    /**
     * Called when an error occurs.
     * Default implementation prints the error.
     *
     * @param error The error that occurred
     */
    default void onError(Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
    }

    /**
     * Called when subscription is confirmed.
     * Default implementation does nothing.
     *
     * @param marketTicker The market ticker that was subscribed
     */
    default void onSubscribed(String marketTicker) {
    }
}
