package com.kalshi.client.websocket;

import com.kalshi.client.model.Market;
import com.kalshi.client.model.MarketLifecycleEvent;

import java.time.Instant;

/**
 * Listener interface for market lifecycle events.
 * Implement this interface to receive notifications about market status changes.
 */
public interface MarketLifecycleListener {

    /**
     * Called when successfully connected to the WebSocket.
     */
    default void onConnected() {
    }

    /**
     * Called when disconnected from the WebSocket.
     *
     * @param code   WebSocket close code
     * @param reason Close reason
     */
    default void onDisconnected(int code, String reason) {
    }

    /**
     * Called when an error occurs.
     *
     * @param error The error
     */
    default void onError(Throwable error) {
    }

    /**
     * Called when a new market is created.
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     */
    default void onMarketCreated(String marketTicker, Market market) {
    }

    /**
     * Called when a market is activated for trading.
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     */
    default void onMarketActivated(String marketTicker, Market market) {
    }

    /**
     * Called when a market is deactivated (trading suspended).
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     */
    default void onMarketDeactivated(String marketTicker, Market market) {
    }

    /**
     * Called when a market's close date is updated.
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     * @param newCloseTime The new close time
     */
    default void onMarketCloseDateUpdated(String marketTicker, Market market, Instant newCloseTime) {
    }

    /**
     * Called when a market outcome is determined.
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     * @param result       The market result (e.g., "yes", "no")
     */
    default void onMarketDetermined(String marketTicker, Market market, String result) {
    }

    /**
     * Called when a market is settled (payouts processed).
     *
     * @param marketTicker The market ticker
     * @param market       The market data (may be null)
     */
    default void onMarketSettled(String marketTicker, Market market) {
    }

    /**
     * Called for any lifecycle event. This is a catch-all method that receives
     * all events, including those not covered by the specific methods above.
     *
     * @param event The lifecycle event
     */
    default void onLifecycleEvent(MarketLifecycleEvent event) {
    }
}
