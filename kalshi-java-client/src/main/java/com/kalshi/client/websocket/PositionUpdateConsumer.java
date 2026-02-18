package com.kalshi.client.websocket;

import com.kalshi.client.model.Position;

/**
 * Consumer interface for receiving position updates from the WebSocket.
 *
 * <p>Implement this interface to handle position updates, connection events,
 * and errors from the {@link PositionWebSocketClient}.</p>
 */
public interface PositionUpdateConsumer {

    /**
     * Called when the WebSocket connection is established.
     */
    default void onConnected() {}

    /**
     * Called when a position update is received.
     * This includes both initial positions and subsequent changes.
     *
     * @param position The updated position
     */
    void onPositionUpdate(Position position);

    /**
     * Called when the WebSocket connection is closed.
     *
     * @param code The close code
     * @param reason The close reason
     */
    default void onDisconnected(int code, String reason) {}

    /**
     * Called when an error occurs.
     *
     * @param error The error that occurred
     */
    default void onError(Throwable error) {}
}
