package com.kalshi.sample.ui;

import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.strategy.EventStrategy;
import com.kalshi.client.strategy.TradingStrategy;
import javafx.application.Platform;
import javafx.scene.control.Tab;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Tab that contains a StrategyStatusPanel for a single trading strategy.
 * Handles wiring up data updates and lifecycle management.
 */
public class StrategyTab extends Tab {

    private final TradingStrategy strategy;
    private final StrategyStatusPanel statusPanel;

    private OrderManager orderManager;
    private PositionManager positionManager;
    private MarketManager marketManager;

    // Listeners for cleanup
    private Consumer<OrderManager.OrderChangeEvent> orderListener;
    private Consumer<PositionManager.PositionChangeEvent> positionListener;
    private Consumer<MarketManager.MarketChangeEvent> marketListener;
    private TradingStrategy.MarketDataLabelListener marketDataLabelListener;

    public StrategyTab(TradingStrategy strategy) {
        this.strategy = strategy;
        this.statusPanel = new StrategyStatusPanel(strategy);

        // Set tab title - use event title for EventStrategy, otherwise strategy name
        String tabTitle;
        if (strategy instanceof EventStrategy) {
            tabTitle = ((EventStrategy) strategy).getEventTitle();
        } else {
            tabTitle = strategy.getStrategyName();
        }
        setText(tabTitle);

        // Set content
        setContent(statusPanel);

        // Setup market data label listener (posts updates to FX thread)
        marketDataLabelListener = labelText -> Platform.runLater(() -> {
            statusPanel.updateMarketDataLabel(labelText);
        });
        strategy.addMarketDataLabelListener(marketDataLabelListener);

        // Handle tab close
        setOnClosed(e -> dispose());
    }

    /**
     * Wire up the managers for data updates.
     *
     * @param orderManager    Order manager for order updates
     * @param positionManager Position manager for position updates
     * @param marketManager   Market manager for orderbook updates
     */
    public void wireManagers(OrderManager orderManager, PositionManager positionManager, MarketManager marketManager) {
        this.orderManager = orderManager;
        this.positionManager = positionManager;
        this.marketManager = marketManager;

        statusPanel.setMarketManager(marketManager);

        // Setup order listener
        orderListener = event -> Platform.runLater(() -> {
            List<Order> orders = orderManager.getAllOrders();
            statusPanel.updateOrders(orders);
        });
        orderManager.addOrderChangeListener(orderListener);

        // Setup position listener
        positionListener = event -> Platform.runLater(() -> {
            List<Position> positions = positionManager.getAllPositions();
            statusPanel.updatePositions(positions);
        });
        positionManager.addPositionChangeListener(positionListener);

        // Setup market listener for orderbook updates
        marketListener = event -> Platform.runLater(() -> {
            statusPanel.updateOrderbooks();
        });
        marketManager.addMarketChangeListener(marketListener);

        // Initial data load
        Platform.runLater(() -> {
            statusPanel.updateOrders(orderManager.getAllOrders());
            statusPanel.updatePositions(positionManager.getAllPositions());
            statusPanel.updateOrderbooks();
        });
    }

    /**
     * Set a market info provider for looking up market details.
     */
    public void setMarketInfoProvider(Function<String, Market> provider) {
        statusPanel.setMarketInfoProvider(provider);
    }

    /**
     * Get the underlying strategy.
     */
    public TradingStrategy getStrategy() {
        return strategy;
    }

    /**
     * Get the status panel for direct access.
     */
    public StrategyStatusPanel getStatusPanel() {
        return statusPanel;
    }

    /**
     * Refresh all displays.
     */
    public void refresh() {
        if (orderManager != null) {
            statusPanel.updateOrders(orderManager.getAllOrders());
        }
        if (positionManager != null) {
            statusPanel.updatePositions(positionManager.getAllPositions());
        }
        statusPanel.updateOrderbooks();
        statusPanel.refreshHeader();
    }

    /**
     * Clean up resources when the tab is closed.
     */
    public void dispose() {
        // Remove listeners
        if (orderManager != null && orderListener != null) {
            orderManager.removeOrderChangeListener(orderListener);
        }
        if (positionManager != null && positionListener != null) {
            positionManager.removePositionChangeListener(positionListener);
        }
        if (marketManager != null && marketListener != null) {
            marketManager.removeMarketChangeListener(marketListener);
        }

        // Remove market data label listener
        if (marketDataLabelListener != null) {
            strategy.removeMarketDataLabelListener(marketDataLabelListener);
        }

        // Dispose panel
        statusPanel.dispose();
    }
}
