package com.kalshi.sample.strategy;

import com.kalshi.client.manager.ManagedMarket;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import com.kalshi.client.strategy.TradingStrategy;

/**
 * Example trading strategy implementation.
 * Demonstrates how to extend TradingStrategy and use its features.
 *
 * <p>Note: Risk checks are configured and enforced by the application,
 * not by individual strategies. Strategies cannot enable or disable risk checks.
 * Per-strategy limits can be configured in the application's RiskConfig.</p>
 *
 * <p>Run with: java ... --strategy=com.kalshi.sample.strategy.ExampleStrategy</p>
 */
public class ExampleStrategy extends TradingStrategy {

    // ==================== Lifecycle Callbacks ====================

    @Override
    public void onInitialized() {
        log.info("ExampleStrategy initialized!");
        log.info("Order service: {}", getOrderService() != null ? "available" : "unavailable");
        log.info("Order manager: {}", getOrderManager() != null ? "available" : "unavailable");
        log.info("Position manager: {}", getPositionManager() != null ? "available" : "unavailable");
        log.info("Market manager: {}", getMarketManager() != null ? "available" : "unavailable");

        // Example: Subscribe to market updates
        // getMarketManager().subscribe("KXBTC-25JAN10");

        // Example: Add listener for market changes
        getMarketManager().addMarketChangeListener(event -> {
            if (event.getType() == MarketManager.MarketChangeType.ORDERBOOK_DELTA ||
                event.getType() == MarketManager.MarketChangeType.ORDERBOOK_SNAPSHOT) {
                onOrderbookUpdate(event.getMarket());
            }
        });
    }

    @Override
    public void onShutdown() {
        log.info("ExampleStrategy shutting down...");
    }

    // ==================== Timer Callback ====================

    @Override
    public void onTimer() {
        // Called every 5 seconds (default interval)
        log.debug("Timer tick - Orders: {}, Positions: {}",
            getOrderManager().getAllOrders().size(),
            getPositionManager().getAllPositions().size());

        // Example: Check market conditions, update state, etc.
        // ManagedMarket market = getMarketManager().getMarket("KXBTC-25JAN10");
        // if (market != null && market.getYesSpread() != null && market.getYesSpread() > 10) {
        //     log.info("Wide spread detected: {}c", market.getYesSpread());
        // }
    }

    // Override to customize timer interval (optional)
    // @Override
    // protected long getTimerIntervalSeconds() {
    //     return 10; // 10 seconds instead of default 5
    // }

    // Override to disable timer (optional)
    // @Override
    // protected boolean isTimerEnabled() {
    //     return false;
    // }

    // ==================== Order Callbacks ====================

    @Override
    public void onOrderCreated(Order order) {
        log.info("Order created: {} - {} {} {} @ {}c",
            order.getOrderId(), order.getAction(), order.getSide(), order.getTicker(),
            order.getYesPrice() != null ? order.getYesPrice() : order.getNoPrice());
    }

    @Override
    public void onOrderModified(Order order) {
        log.info("Order modified: {} - remaining: {}", order.getOrderId(), order.getRemainingCount());
    }

    @Override
    public void onOrderRemoved(Order order) {
        log.info("Order removed: {}", order.getOrderId());
    }

    // ==================== Position Callbacks ====================

    @Override
    public void onPositionOpened(Position position) {
        log.info("Position opened: {} - {} contracts",
            position.getMarketTicker(), position.getPosition());
    }

    @Override
    public void onPositionUpdated(Position position) {
        log.info("Position updated: {} - {} contracts - P&L: ${}",
            position.getMarketTicker(), position.getPosition(), position.getRealizedPnlDollars());
    }

    @Override
    public void onPositionClosed(Position position) {
        log.info("Position closed: {} - Final P&L: ${}",
            position.getMarketTicker(), position.getRealizedPnlDollars());
    }

    // ==================== Custom Methods ====================

    private void onOrderbookUpdate(ManagedMarket market) {
        if (market == null) return;

        Integer bestBid = market.getBestYesBid();
        Integer bestAsk = market.getBestYesAsk();
        Integer spread = market.getYesSpread();

        // Example: Log orderbook updates
        // log.debug("Orderbook update for {} - Bid: {}c, Ask: {}c, Spread: {}c",
        //     market.getTicker(), bestBid, bestAsk, spread);

        // Example: Simple strategy logic
        // if (spread != null && spread > 5) {
        //     log.info("Wide spread detected on {}: {}c", market.getTicker(), spread);
        // }
    }

    /**
     * Example method showing how to place an order.
     * Risk checks are automatically performed by the application before order submission.
     * The application configures the limits in RiskConfig (see OrderbookFxApp).
     */
    public void placeExampleOrder(String ticker) {
        try {
            // Buy 1 yes contract at 50 cents
            // Risk checker (configured by the application) will validate this order
            Order order = buy(ticker, "yes", 1, 50);
            log.info("Order placed: {}", order.getOrderId());
        } catch (com.kalshi.client.risk.RiskCheckException e) {
            // Handle risk check failures
            log.warn("Order rejected by risk check: {} - Actual: {}, Limit: {}",
                e.getCheckType(), e.getActualValue(), e.getLimitValue());
        } catch (Exception e) {
            log.error("Failed to place order: {}", e.getMessage(), e);
        }
    }

    /**
     * Example method showing an order that would fail risk checks.
     * The application has configured maxOrderQuantity=100, so this will be rejected.
     */
    public void placeRiskyOrder(String ticker) {
        try {
            // Try to buy 200 contracts - exceeds application's maxOrderQuantity of 100
            Order order = buy(ticker, "yes", 200, 50);
            log.info("Order placed: {}", order.getOrderId());
        } catch (com.kalshi.client.risk.RiskCheckException e) {
            log.warn("Order correctly rejected: {}", e.getMessage());
        }
    }
}
