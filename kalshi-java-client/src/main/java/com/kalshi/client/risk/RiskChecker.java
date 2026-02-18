package com.kalshi.client.risk;

import com.kalshi.client.model.CreateOrderRequest;
import com.kalshi.client.model.AmendOrderRequest;
import com.kalshi.client.model.Order;
import com.kalshi.client.model.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Performs risk checks on orders before they are submitted.
 * Validates against configured limits for quantity and notional values.
 * Supports per-strategy limit overrides.
 *
 * <p>This class is designed to be instantiated and configured by the application,
 * not by individual strategies. Strategies cannot enable or disable risk checks.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Application creates and configures risk checker
 * RiskConfig config = RiskConfig.builder()
 *     .maxOrderQuantity(100)
 *     .maxOrderNotional(5000)
 *     .forStrategy("AggressiveStrategy")
 *         .maxOrderQuantity(50)
 *         .done()
 *     .build();
 *
 * RiskChecker checker = new RiskChecker(config);
 * checker.setPositionProvider(ticker -> positionManager.getPosition(ticker));
 *
 * // Set on OrderService
 * orderService.setRiskChecker(checker);
 *
 * // Set current strategy context before order operations
 * checker.setCurrentStrategy("AggressiveStrategy");
 * }</pre>
 */
public class RiskChecker {

    private static final Logger log = LoggerFactory.getLogger(RiskChecker.class);

    private final RiskConfig config;
    private Function<String, Position> positionProvider;
    private volatile String currentStrategy;
    private final List<Consumer<RiskViolation>> violationListeners = new CopyOnWriteArrayList<>();

    /**
     * Create a RiskChecker with the given configuration.
     *
     * @param config Risk configuration with limits
     */
    public RiskChecker(RiskConfig config) {
        this.config = config != null ? config : RiskConfig.disabled();
        log.info("RiskChecker initialized: {}", this.config);
    }

    /**
     * Set the position provider for position-level checks.
     * The provider should return the current position for a given ticker.
     *
     * @param positionProvider Function that returns Position for a ticker
     */
    public void setPositionProvider(Function<String, Position> positionProvider) {
        this.positionProvider = positionProvider;
        log.info("Position provider configured for risk checks");
    }

    /**
     * Set the current strategy context for risk checks.
     * This determines which limits are used (strategy-specific or global).
     *
     * @param strategyName The strategy class simple name (e.g., "MyStrategy")
     */
    public void setCurrentStrategy(String strategyName) {
        this.currentStrategy = strategyName;
        if (strategyName != null && config.hasStrategyLimits(strategyName)) {
            log.debug("Using strategy-specific limits for: {}", strategyName);
        }
    }

    /**
     * Get the current strategy context.
     */
    public String getCurrentStrategy() {
        return currentStrategy;
    }

    /**
     * Clear the current strategy context (use global limits).
     */
    public void clearCurrentStrategy() {
        this.currentStrategy = null;
    }

    /**
     * Get the current risk configuration.
     */
    public RiskConfig getConfig() {
        return config;
    }

    /**
     * Check if risk checks are enabled.
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Add a listener for risk violations.
     * The listener will be notified whenever a risk check fails.
     *
     * @param listener The listener to add
     */
    public void addViolationListener(Consumer<RiskViolation> listener) {
        if (listener != null && !violationListeners.contains(listener)) {
            violationListeners.add(listener);
        }
    }

    /**
     * Remove a violation listener.
     *
     * @param listener The listener to remove
     */
    public void removeViolationListener(Consumer<RiskViolation> listener) {
        violationListeners.remove(listener);
    }

    /**
     * Notify all listeners of a violation.
     */
    private void notifyViolation(RiskCheckException ex) {
        RiskViolation violation = RiskViolation.fromException(ex, currentStrategy);
        for (Consumer<RiskViolation> listener : violationListeners) {
            try {
                listener.accept(violation);
            } catch (Exception e) {
                log.error("Error in violation listener", e);
            }
        }
    }

    /**
     * Validate a new order against risk limits.
     *
     * @param request The order creation request
     * @throws RiskCheckException if any risk check fails
     */
    public void checkOrder(CreateOrderRequest request) {
        if (!config.isEnabled()) {
            return;
        }

        String ticker = request.getTicker();
        int quantity = request.getCount();
        int price = getPrice(request);
        String strategy = currentStrategy;

        log.debug("Checking order: {} qty={} price={}¢ strategy={}", ticker, quantity, price, strategy);

        // Check order quantity limit
        checkOrderQuantity(quantity, strategy);

        // Check order notional limit
        checkOrderNotional(quantity, price, strategy);

        // Check position limits (if position provider is set)
        if (positionProvider != null) {
            Position currentPosition = positionProvider.apply(ticker);
            checkPositionQuantity(ticker, quantity, currentPosition, strategy);
            checkPositionNotional(ticker, quantity, price, currentPosition, strategy);
        }

        log.debug("Order passed all risk checks: {} qty={} price={}¢", ticker, quantity, price);
    }

    /**
     * Validate an order amendment against risk limits.
     *
     * @param orderId The order ID being amended
     * @param currentOrder The current order state
     * @param request The amendment request
     * @throws RiskCheckException if any risk check fails
     */
    public void checkAmendment(String orderId, Order currentOrder, AmendOrderRequest request) {
        if (!config.isEnabled()) {
            return;
        }

        String ticker = currentOrder.getTicker();
        String strategy = currentStrategy;

        // Determine the new quantity (use amendment value or keep current)
        int newQuantity = request.getCount() != null ? request.getCount() :
                (currentOrder.getRemainingCount() != null ? currentOrder.getRemainingCount() : 0);

        // Determine the new price (use amendment value or keep current)
        int newPrice = getAmendmentPrice(request, currentOrder);

        log.debug("Checking amendment for {}: qty={} price={}¢ strategy={}", orderId, newQuantity, newPrice, strategy);

        // Check order quantity limit
        checkOrderQuantity(newQuantity, strategy);

        // Check order notional limit
        checkOrderNotional(newQuantity, newPrice, strategy);

        // Check position limits (if position provider is set)
        if (positionProvider != null) {
            Position currentPosition = positionProvider.apply(ticker);

            // For amendments, we need to consider the change in quantity
            int currentOrderQty = currentOrder.getRemainingCount() != null ? currentOrder.getRemainingCount() : 0;
            int quantityChange = newQuantity - currentOrderQty;

            checkPositionQuantity(ticker, quantityChange, currentPosition, strategy);
            checkPositionNotional(ticker, quantityChange, newPrice, currentPosition, strategy);
        }

        log.debug("Amendment passed all risk checks: {} qty={} price={}¢", orderId, newQuantity, newPrice);
    }

    // ==================== Individual Checks ====================

    private void checkOrderQuantity(int quantity, String strategy) {
        if (config.hasMaxOrderQuantity(strategy)) {
            int limit = config.getMaxOrderQuantity(strategy);
            if (quantity > limit) {
                String msg = String.format("Order quantity %d exceeds maximum allowed %d%s",
                        quantity, limit, strategy != null ? " for " + strategy : "");
                log.warn("Risk check failed: {}", msg);
                RiskCheckException ex = new RiskCheckException(
                        RiskCheckException.RiskCheckType.MAX_ORDER_QUANTITY,
                        msg, quantity, limit);
                notifyViolation(ex);
                throw ex;
            }
        }
    }

    private void checkOrderNotional(int quantity, int priceInCents, String strategy) {
        if (config.hasMaxOrderNotional(strategy)) {
            int limit = config.getMaxOrderNotional(strategy);
            int notional = quantity * priceInCents;
            if (notional > limit) {
                String msg = String.format("Order notional %d¢ ($%.2f) exceeds maximum allowed %d¢ ($%.2f)%s",
                        notional, notional / 100.0,
                        limit, limit / 100.0,
                        strategy != null ? " for " + strategy : "");
                log.warn("Risk check failed: {}", msg);
                RiskCheckException ex = new RiskCheckException(
                        RiskCheckException.RiskCheckType.MAX_ORDER_NOTIONAL,
                        msg, notional, limit);
                notifyViolation(ex);
                throw ex;
            }
        }
    }

    private void checkPositionQuantity(String ticker, int additionalQuantity, Position currentPosition, String strategy) {
        if (!config.hasMaxPositionQuantity(strategy)) {
            return;
        }

        int limit = config.getMaxPositionQuantity(strategy);
        int currentQty = 0;
        if (currentPosition != null && currentPosition.getPosition() != null) {
            currentQty = Math.abs(currentPosition.getPosition());
        }

        int projectedQty = currentQty + Math.abs(additionalQuantity);

        if (projectedQty > limit) {
            String msg = String.format("Projected position quantity %d for %s exceeds maximum allowed %d (current: %d, order: %d)%s",
                    projectedQty, ticker, limit, currentQty, additionalQuantity,
                    strategy != null ? " for " + strategy : "");
            log.warn("Risk check failed: {}", msg);
            RiskCheckException ex = new RiskCheckException(
                    RiskCheckException.RiskCheckType.MAX_POSITION_QUANTITY,
                    msg, projectedQty, limit, ticker);
            notifyViolation(ex);
            throw ex;
        }
    }

    private void checkPositionNotional(String ticker, int additionalQuantity, int priceInCents, Position currentPosition, String strategy) {
        if (!config.hasMaxPositionNotional(strategy)) {
            return;
        }

        int limit = config.getMaxPositionNotional(strategy);

        // Position cost is in centi-cents (1/100 of a cent), convert to cents
        long currentNotional = 0;
        if (currentPosition != null && currentPosition.getPositionCost() != null) {
            // Convert from centi-cents to cents
            currentNotional = Math.abs(currentPosition.getPositionCost() / 100);
        }

        long additionalNotional = Math.abs((long) additionalQuantity * priceInCents);
        long projectedNotional = currentNotional + additionalNotional;

        if (projectedNotional > limit) {
            String msg = String.format("Projected position notional %d¢ ($%.2f) for %s exceeds maximum allowed %d¢ ($%.2f)%s",
                    projectedNotional, projectedNotional / 100.0, ticker,
                    limit, limit / 100.0,
                    strategy != null ? " for " + strategy : "");
            log.warn("Risk check failed: {}", msg);
            RiskCheckException ex = new RiskCheckException(
                    RiskCheckException.RiskCheckType.MAX_POSITION_NOTIONAL,
                    msg, projectedNotional, limit, ticker);
            notifyViolation(ex);
            throw ex;
        }
    }

    // ==================== Helper Methods ====================

    private int getPrice(CreateOrderRequest request) {
        if (request.getYesPrice() != null) {
            return request.getYesPrice();
        } else if (request.getNoPrice() != null) {
            return request.getNoPrice();
        }
        return 50; // Default to 50 cents if no price specified
    }

    private int getAmendmentPrice(AmendOrderRequest request, Order currentOrder) {
        if (request.getYesPrice() != null) {
            return request.getYesPrice();
        } else if (request.getNoPrice() != null) {
            return request.getNoPrice();
        } else if (currentOrder.getYesPrice() != null) {
            return currentOrder.getYesPrice();
        } else if (currentOrder.getNoPrice() != null) {
            return currentOrder.getNoPrice();
        }
        return 50; // Default to 50 cents if no price specified
    }
}
