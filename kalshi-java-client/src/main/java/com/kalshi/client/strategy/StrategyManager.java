package com.kalshi.client.strategy;

import com.betting.marketdata.api.MarketDataManager;
import com.kalshi.client.KalshiApi;
import com.kalshi.client.filter.EventInterestList;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Manages multiple TradingStrategy instances, particularly EventStrategy instances
 * created from an EventInterestList.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Creates EventStrategy instances from EventInterestList</li>
 *   <li>Initializes all strategies with required services</li>
 *   <li>Manages strategy lifecycle (start, stop, shutdown)</li>
 *   <li>Provides access to strategies by event ticker</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Create filter and get interest list
 * EventFilter filter = new EventFilter(kalshiApi);
 * EventInterestList interestList = filter.filter(
 *     Arrays.asList("KXINX", "KXBTC"),
 *     EventFilter.defaultCriteria()
 * );
 *
 * // Create strategy manager
 * StrategyManager manager = new StrategyManager(kalshiApi, orderManager, positionManager, marketManager);
 *
 * // Create strategies from interest list
 * manager.createStrategies(interestList, eventTicker -> new IndexEventStrategy(eventTicker));
 *
 * // Or use a specific strategy class
 * manager.createStrategies(interestList, IndexEventStrategy.class);
 *
 * // Initialize all strategies
 * manager.initializeAll();
 *
 * // Access strategies
 * EventStrategy strategy = manager.getStrategy("KXINX-25JAN13");
 *
 * // Shutdown all strategies
 * manager.shutdownAll();
 * }</pre>
 */
public class StrategyManager {

    private static final Logger log = LoggerFactory.getLogger(StrategyManager.class);

    private final KalshiApi api;
    private final OrderManager orderManager;
    private final PositionManager positionManager;
    private final MarketManager marketManager;
    private final MarketDataManager marketDataManager;  // External market data (E*TRADE, etc.)

    // Managed strategies by event ticker
    private final Map<String, EventStrategy> strategies = new ConcurrentHashMap<>();

    // General trading strategies (not event-specific)
    private final Map<String, TradingStrategy> generalStrategies = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;

    /**
     * Get a log-friendly identifier for an EventStrategy (includes event title if available).
     */
    private String getStrategyLogId(EventStrategy strategy) {
        if (strategy == null) return "null";
        String title = strategy.getEventTitle();
        if (title != null && !title.equals(strategy.getEventTicker())) {
            return strategy.getEventTicker() + "|" + title;
        }
        return strategy.getEventTicker();
    }

    /**
     * Create a StrategyManager without external market data.
     *
     * @param api KalshiApi instance
     * @param orderManager OrderManager instance
     * @param positionManager PositionManager instance
     * @param marketManager MarketManager instance
     */
    public StrategyManager(KalshiApi api, OrderManager orderManager,
                           PositionManager positionManager, MarketManager marketManager) {
        this(api, orderManager, positionManager, marketManager, null);
    }

    /**
     * Create a StrategyManager with external market data support.
     *
     * @param api KalshiApi instance
     * @param orderManager OrderManager instance
     * @param positionManager PositionManager instance
     * @param marketManager MarketManager instance
     * @param marketDataManager External market data manager (can be null)
     */
    public StrategyManager(KalshiApi api, OrderManager orderManager,
                           PositionManager positionManager, MarketManager marketManager,
                           MarketDataManager marketDataManager) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.orderManager = Objects.requireNonNull(orderManager, "orderManager must not be null");
        this.positionManager = Objects.requireNonNull(positionManager, "positionManager must not be null");
        this.marketManager = Objects.requireNonNull(marketManager, "marketManager must not be null");
        this.marketDataManager = marketDataManager;  // can be null
    }

    // ==================== Strategy Creation ====================

    /**
     * Create EventStrategy instances from an interest list using a factory function.
     *
     * @param interestList EventInterestList to create strategies from
     * @param strategyFactory Function that creates a strategy for an event ticker
     * @return Number of strategies created
     */
    public int createStrategies(EventInterestList interestList,
                                Function<String, ? extends EventStrategy> strategyFactory) {
        if (interestList == null || interestList.isEmpty()) {
            log.warn("Empty interest list, no strategies created");
            return 0;
        }

        int created = 0;
        for (String eventTicker : interestList.getEventTickers()) {
            if (strategies.containsKey(eventTicker)) {
                log.debug("Strategy already exists for event: {}", eventTicker);
                continue;
            }

            try {
                EventStrategy strategy = strategyFactory.apply(eventTicker);
                if (strategy != null) {
                    strategies.put(eventTicker, strategy);
                    created++;
                    log.info("Created strategy for event: {}", eventTicker);
                }
            } catch (Exception e) {
                log.error("Failed to create strategy for event {}: {}", eventTicker, e.getMessage(), e);
            }
        }

        log.info("Created {} strategies from {} events in interest list",
                created, interestList.size());
        return created;
    }

    /**
     * Create EventStrategy instances using reflection.
     *
     * @param interestList EventInterestList to create strategies from
     * @param strategyClass Strategy class with a constructor that takes event ticker
     * @return Number of strategies created
     */
    public <T extends EventStrategy> int createStrategies(EventInterestList interestList, Class<T> strategyClass) {
        return createStrategies(interestList, eventTicker -> {
            try {
                return strategyClass.getDeclaredConstructor(String.class).newInstance(eventTicker);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate strategy class: " + strategyClass.getName(), e);
            }
        });
    }

    /**
     * Create a single EventStrategy for an event ticker.
     *
     * @param eventTicker Event ticker
     * @param strategyFactory Factory function to create the strategy
     * @return The created strategy, or null if creation failed
     */
    public EventStrategy createStrategy(String eventTicker,
                                         Function<String, ? extends EventStrategy> strategyFactory) {
        if (strategies.containsKey(eventTicker)) {
            log.warn("Strategy already exists for event: {}", eventTicker);
            return strategies.get(eventTicker);
        }

        try {
            EventStrategy strategy = strategyFactory.apply(eventTicker);
            if (strategy != null) {
                strategies.put(eventTicker, strategy);
                log.info("Created strategy for event: {}", eventTicker);
            }
            return strategy;
        } catch (Exception e) {
            log.error("Failed to create strategy for event {}: {}", eventTicker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Add a pre-created EventStrategy.
     *
     * @param strategy EventStrategy to add
     */
    public void addStrategy(EventStrategy strategy) {
        if (strategy == null) return;
        String eventTicker = strategy.getEventTicker();
        strategies.put(eventTicker, strategy);
        log.info("Added strategy for event: {}", eventTicker);
    }

    /**
     * Add a general (non-event) trading strategy.
     *
     * @param name Strategy name/identifier
     * @param strategy TradingStrategy instance
     */
    public void addGeneralStrategy(String name, TradingStrategy strategy) {
        if (strategy == null || name == null) return;
        generalStrategies.put(name, strategy);
        log.info("Added general strategy: {}", name);
    }

    // ==================== Initialization ====================

    /**
     * Initialize all strategies.
     * Must be called after strategies are created.
     * Strategies that fail to initialize with {@link NoMarketsTrackedException} are automatically removed.
     *
     * @return Number of strategies that failed initialization and were removed
     */
    public int initializeAll() {
        log.info("Initializing {} event strategies and {} general strategies",
                strategies.size(), generalStrategies.size());

        int removed = 0;

        // Initialize event strategies, removing ones that fail due to no tracked markets
        Iterator<Map.Entry<String, EventStrategy>> iter = strategies.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, EventStrategy> entry = iter.next();
            try {
                initializeStrategy(entry.getValue());
            } catch (NoMarketsTrackedException e) {
                String logId = getStrategyLogId(entry.getValue());
                log.warn("Removing strategy {} - no markets tracked: {}", logId, e.getMessage());
                iter.remove();
                removed++;
            }
        }

        // Initialize general strategies
        for (Map.Entry<String, TradingStrategy> entry : generalStrategies.entrySet()) {
            initializeStrategy(entry.getValue());
        }

        initialized = true;
        log.info("All strategies initialized ({} removed due to no tracked markets)", removed);
        return removed;
    }

    /**
     * Initialize a specific strategy.
     *
     * @throws NoMarketsTrackedException if the strategy has no markets to track
     */
    private void initializeStrategy(TradingStrategy strategy) {
        if (strategy.isInitialized()) {
            log.debug("Strategy already initialized: {}", strategy.getStrategyName());
            return;
        }

        try {
            // initialize() calls onInitialized() internally
            strategy.initialize(api, orderManager, positionManager, marketManager, marketDataManager);
        } catch (NoMarketsTrackedException e) {
            // Re-throw to allow caller to handle (e.g., remove from management)
            throw e;
        } catch (Exception e) {
            if (strategy instanceof EventStrategy) {
                log.error("Failed to initialize strategy {}: {}", getStrategyLogId((EventStrategy) strategy), e.getMessage(), e);
            } else {
                log.error("Failed to initialize strategy {}: {}", strategy.getStrategyName(), e.getMessage(), e);
            }
        }
    }

    /**
     * Initialize a single strategy by event ticker.
     *
     * @param eventTicker Event ticker
     * @return true if initialization succeeded
     */
    public boolean initializeStrategy(String eventTicker) {
        EventStrategy strategy = strategies.get(eventTicker);
        if (strategy == null) {
            log.warn("No strategy found for event: {}", eventTicker);
            return false;
        }

        initializeStrategy(strategy);
        return strategy.isInitialized();
    }

    // ==================== Strategy Access ====================

    /**
     * Get an EventStrategy by event ticker.
     *
     * @param eventTicker Event ticker
     * @return EventStrategy or null if not found
     */
    public EventStrategy getStrategy(String eventTicker) {
        return strategies.get(eventTicker);
    }

    /**
     * Get a general strategy by name.
     *
     * @param name Strategy name
     * @return TradingStrategy or null if not found
     */
    public TradingStrategy getGeneralStrategy(String name) {
        return generalStrategies.get(name);
    }

    /**
     * Get all EventStrategy instances.
     *
     * @return Unmodifiable collection of strategies
     */
    public Collection<EventStrategy> getAllStrategies() {
        return Collections.unmodifiableCollection(strategies.values());
    }

    /**
     * Get all general strategies.
     *
     * @return Unmodifiable collection of general strategies
     */
    public Collection<TradingStrategy> getAllGeneralStrategies() {
        return Collections.unmodifiableCollection(generalStrategies.values());
    }

    /**
     * Get all event tickers with strategies.
     *
     * @return Set of event tickers
     */
    public Set<String> getEventTickers() {
        return Collections.unmodifiableSet(strategies.keySet());
    }

    /**
     * Get number of event strategies.
     */
    public int getStrategyCount() {
        return strategies.size();
    }

    /**
     * Get number of general strategies.
     */
    public int getGeneralStrategyCount() {
        return generalStrategies.size();
    }

    /**
     * Check if manager has been initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if a strategy exists for an event ticker.
     *
     * @param eventTicker Event ticker
     * @return true if a strategy exists
     */
    public boolean hasStrategy(String eventTicker) {
        return strategies.containsKey(eventTicker);
    }

    // ==================== Strategy Removal ====================

    /**
     * Remove and shutdown a strategy by event ticker.
     *
     * @param eventTicker Event ticker
     * @return The removed strategy, or null if not found
     */
    public EventStrategy removeStrategy(String eventTicker) {
        EventStrategy strategy = strategies.remove(eventTicker);
        if (strategy != null) {
            String logId = getStrategyLogId(strategy);
            try {
                strategy.shutdown();
                log.info("Removed and shutdown strategy: {}", logId);
            } catch (Exception e) {
                log.error("Error shutting down strategy {}: {}", logId, e.getMessage());
            }
        }
        return strategy;
    }

    /**
     * Remove and shutdown a general strategy.
     *
     * @param name Strategy name
     * @return The removed strategy, or null if not found
     */
    public TradingStrategy removeGeneralStrategy(String name) {
        TradingStrategy strategy = generalStrategies.remove(name);
        if (strategy != null) {
            try {
                strategy.shutdown();
                log.info("Removed and shutdown general strategy: {}", name);
            } catch (Exception e) {
                log.error("Error shutting down general strategy {}: {}", name, e.getMessage());
            }
        }
        return strategy;
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown all strategies.
     */
    public void shutdownAll() {
        log.info("Shutting down all strategies");

        // Shutdown event strategies
        for (Map.Entry<String, EventStrategy> entry : strategies.entrySet()) {
            String logId = getStrategyLogId(entry.getValue());
            try {
                entry.getValue().shutdown();
                log.debug("Shutdown strategy: {}", logId);
            } catch (Exception e) {
                log.error("Error shutting down strategy {}: {}", logId, e.getMessage());
            }
        }

        // Shutdown general strategies
        for (Map.Entry<String, TradingStrategy> entry : generalStrategies.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.debug("Shutdown general strategy: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Error shutting down general strategy {}: {}", entry.getKey(), e.getMessage());
            }
        }

        strategies.clear();
        generalStrategies.clear();
        initialized = false;

        log.info("All strategies shutdown");
    }

    /**
     * Shutdown a specific event strategy.
     *
     * @param eventTicker Event ticker
     */
    public void shutdownStrategy(String eventTicker) {
        EventStrategy strategy = strategies.get(eventTicker);
        if (strategy != null) {
            String logId = getStrategyLogId(strategy);
            try {
                strategy.shutdown();
                log.info("Shutdown strategy: {}", logId);
            } catch (Exception e) {
                log.error("Error shutting down strategy {}: {}", logId, e.getMessage());
            }
        }
    }

    // ==================== Activation ====================

    /**
     * Activate a strategy by event ticker.
     * Subscribes to market data and starts the timer.
     *
     * @param eventTicker Event ticker
     * @return true if activated successfully
     */
    public boolean activateStrategy(String eventTicker) {
        EventStrategy strategy = strategies.get(eventTicker);
        if (strategy == null) {
            log.warn("Cannot activate - no strategy found for event: {}", eventTicker);
            return false;
        }

        if (!strategy.isInitialized()) {
            log.warn("Cannot activate - strategy not initialized: {}", getStrategyLogId(strategy));
            return false;
        }

        strategy.makeActive();
        log.info("Activated strategy: {}", getStrategyLogId(strategy));
        return strategy.isActive();
    }

    /**
     * Deactivate a strategy by event ticker.
     * Unsubscribes from market data and stops the timer.
     *
     * @param eventTicker Event ticker
     * @return true if deactivated successfully
     */
    public boolean deactivateStrategy(String eventTicker) {
        EventStrategy strategy = strategies.get(eventTicker);
        if (strategy == null) {
            log.warn("Cannot deactivate - no strategy found for event: {}", eventTicker);
            return false;
        }

        strategy.makeInactive();
        log.info("Deactivated strategy: {}", getStrategyLogId(strategy));
        return !strategy.isActive();
    }

    /**
     * Get all active strategies.
     *
     * @return List of active EventStrategy instances
     */
    public List<EventStrategy> getActiveStrategies() {
        List<EventStrategy> active = new ArrayList<>();
        for (EventStrategy strategy : strategies.values()) {
            if (strategy.isActive()) {
                active.add(strategy);
            }
        }
        return active;
    }

    /**
     * Get all inactive strategies.
     *
     * @return List of inactive EventStrategy instances
     */
    public List<EventStrategy> getInactiveStrategies() {
        List<EventStrategy> inactive = new ArrayList<>();
        for (EventStrategy strategy : strategies.values()) {
            if (!strategy.isActive()) {
                inactive.add(strategy);
            }
        }
        return inactive;
    }

    /**
     * Get the count of active strategies.
     */
    public int getActiveStrategyCount() {
        int count = 0;
        for (EventStrategy strategy : strategies.values()) {
            if (strategy.isActive()) {
                count++;
            }
        }
        return count;
    }

    // ==================== Summary ====================

    /**
     * Get a summary of managed strategies.
     *
     * @return Summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("StrategyManager Summary:\n");
        sb.append("  Initialized: ").append(initialized).append("\n");
        sb.append("  Event Strategies: ").append(strategies.size())
                .append(" (").append(getActiveStrategyCount()).append(" active)\n");
        sb.append("  General Strategies: ").append(generalStrategies.size()).append("\n");

        if (!strategies.isEmpty()) {
            sb.append("  Event Strategy List:\n");
            for (Map.Entry<String, EventStrategy> entry : strategies.entrySet()) {
                EventStrategy s = entry.getValue();
                sb.append("    - ").append(getStrategyLogId(s))
                        .append(": ").append(s.getStrategyName())
                        .append(" (initialized=").append(s.isInitialized())
                        .append(", active=").append(s.isActive())
                        .append(", markets=").append(s.getTrackedMarketCount()).append(")\n");
            }
        }

        if (!generalStrategies.isEmpty()) {
            sb.append("  General Strategy List:\n");
            for (Map.Entry<String, TradingStrategy> entry : generalStrategies.entrySet()) {
                TradingStrategy s = entry.getValue();
                sb.append("    - ").append(entry.getKey())
                        .append(": ").append(s.getStrategyName())
                        .append(" (initialized=").append(s.isInitialized()).append(")\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "StrategyManager{" +
                "eventStrategies=" + strategies.size() +
                ", generalStrategies=" + generalStrategies.size() +
                ", initialized=" + initialized +
                '}';
    }
}
