package com.kalshi.sample;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.KalshiClient;
import com.kalshi.client.auth.KalshiAuthenticator;
import com.kalshi.client.filter.EventFilter;
import com.kalshi.client.filter.EventFilterCriteria;
import com.kalshi.client.filter.EventInterestList;
import com.kalshi.client.manager.MarketManager;
import com.kalshi.client.manager.OrderManager;
import com.kalshi.client.manager.PositionManager;
import com.kalshi.client.model.Event;
import com.kalshi.client.risk.RiskChecker;
import com.kalshi.client.risk.RiskConfig;
import com.kalshi.client.strategy.EventStrategy;
import com.kalshi.client.strategy.StrategyManager;
import com.kalshi.sample.strategy.IndexEventStrategy;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Demonstration of using EventFilter to discover events across multiple series
 * and create EventStrategy instances for each event of interest.
 *
 * <p>This example shows the full workflow:</p>
 * <ol>
 *   <li>Initialize API and managers</li>
 *   <li>Create EventFilter with filter criteria</li>
 *   <li>Get EventInterestList from multiple series</li>
 *   <li>Use StrategyManager to create strategies for each event</li>
 *   <li>Initialize and run strategies</li>
 * </ol>
 *
 * <p>Run with: mvn exec:java -Dexec.mainClass="com.kalshi.sample.MultiEventStrategyDemo"</p>
 */
public class MultiEventStrategyDemo {

    // Default config file path
    private static final String DEFAULT_CONFIG_FILE = "strategy.properties";

    // API credentials - loaded from config or environment
    private String apiKeyId;
    private String privateKeyFile;

    // Series to monitor
    private static final List<String> SERIES_TICKERS = Arrays.asList(
            "KXINX",    // S&P 500 Index
            "KXNDX",    // NASDAQ Index
            "KXBTC"     // Bitcoin price
    );

    public static void main(String[] args) {
        System.out.println("=== Multi-Event Strategy Demo ===\n");

        try {
            new MultiEventStrategyDemo().run();
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void run() throws Exception {
        // Step 0: Load API credentials
        System.out.println("Step 0: Loading API credentials...");
        loadApiCredentials();
        System.out.println("  Credentials loaded\n");

        // Step 1: Initialize API
        System.out.println("Step 1: Initializing API...");
        KalshiApi api = initializeApi();
        System.out.println("  API initialized\n");

        // Step 2: Initialize managers
        System.out.println("Step 2: Initializing managers...");
        OrderManager orderManager = api.getOrderManager();
        PositionManager positionManager = api.getPositionManager();
        MarketManager marketManager = api.getMarketManager();
        orderManager.start();
        positionManager.start();
        System.out.println("  Managers initialized\n");

        // Step 3: Configure risk checker
        System.out.println("Step 3: Configuring risk checker...");
        RiskChecker riskChecker = initializeRiskChecker(positionManager);
        api.orders().setRiskChecker(riskChecker);
        System.out.println("  Risk checker configured\n");

        // Step 4: Create EventFilter and define criteria
        System.out.println("Step 4: Creating event filter...");
        EventFilter eventFilter = EventFilter.builder(api)
                .parallelThreads(4)
                .build();

        EventFilterCriteria criteria = EventFilterCriteria.builder()
                .minStrikeDateFromNow(Duration.ofHours(2))    // At least 2 hours away
                .maxStrikeDateFromNow(Duration.ofDays(3))     // Within 3 days
                .build();

        System.out.println("  Filter created with criteria: " + criteria + "\n");

        // Step 5: Filter events from series
        System.out.println("Step 5: Filtering events from series: " + SERIES_TICKERS);
        EventInterestList interestList = eventFilter.filter(SERIES_TICKERS, criteria);

        System.out.println("\n  Interest List Summary:");
        System.out.println("  - Events found: " + interestList.size());
        System.out.println("  - Total markets: " + interestList.getTotalMarketCount());
        System.out.println("  - Series covered: " + interestList.getSeriesTickers());
        System.out.println();

        if (interestList.isEmpty()) {
            System.out.println("No events match the criteria. Try adjusting the filter.");
            shutdown(orderManager, positionManager, marketManager);
            return;
        }

        // Print events
        System.out.println("  Events of interest:");
        for (Event event : interestList.getEventsSortedByStrikeDate()) {
            System.out.printf("    - %s: %s (%d markets, strikes: %s)%n",
                    event.getEventTicker(),
                    event.getTitle(),
                    event.getMarkets() != null ? event.getMarkets().size() : 0,
                    event.getStrikeDate());
        }
        System.out.println();

        // Step 6: Create StrategyManager and strategies
        System.out.println("Step 6: Creating strategies...");
        StrategyManager strategyManager = new StrategyManager(api, orderManager, positionManager, marketManager);

        // Create IndexEventStrategy for each event
        int created = strategyManager.createStrategies(interestList, eventTicker -> {
            // Custom strategy factory - can customize based on event
            return new IndexEventStrategy(eventTicker);
        });

        System.out.println("  Created " + created + " strategies\n");

        // Step 7: Initialize all strategies
        System.out.println("Step 7: Initializing strategies...");
        strategyManager.initializeAll();
        System.out.println();

        // Print strategy summary
        System.out.println(strategyManager.getSummary());

        // Step 8: Run for a while (or until interrupted)
        System.out.println("\nStep 8: Running strategies (press Ctrl+C to stop)...\n");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down...");
            strategyManager.shutdownAll();
            shutdown(orderManager, positionManager, marketManager);
            System.out.println("Shutdown complete.");
        }));

        // Keep running
        Thread.sleep(Long.MAX_VALUE);
    }

    /**
     * Load API credentials from config file or environment variables.
     */
    private void loadApiCredentials() throws Exception {
        // Try loading from config file first
        Path configPath = Path.of(DEFAULT_CONFIG_FILE);
        if (Files.exists(configPath)) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                props.load(fis);
            }
            String keyId = props.getProperty("api.keyId");
            String keyFile = props.getProperty("api.privateKeyFile");
            if (keyId != null && !keyId.trim().isEmpty()) {
                apiKeyId = keyId.trim();
                System.out.println("  Loaded API Key ID from " + DEFAULT_CONFIG_FILE);
            }
            if (keyFile != null && !keyFile.trim().isEmpty()) {
                privateKeyFile = keyFile.trim();
                System.out.println("  Loaded private key file from " + DEFAULT_CONFIG_FILE);
            }
        }

        // Fall back to environment variables
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            apiKeyId = System.getenv("KALSHI_API_KEY_ID");
            if (apiKeyId != null && !apiKeyId.isEmpty()) {
                System.out.println("  Loaded API Key ID from environment variable");
            }
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            privateKeyFile = System.getenv("KALSHI_PRIVATE_KEY_FILE");
            if (privateKeyFile != null && !privateKeyFile.isEmpty()) {
                System.out.println("  Loaded private key file from environment variable");
            }
        }

        // Validate
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            throw new IllegalStateException(
                "API Key ID not configured. Set via:\n" +
                "  1. Config file (" + DEFAULT_CONFIG_FILE + "): api.keyId=your-key-id\n" +
                "  2. Environment variable: KALSHI_API_KEY_ID");
        }
        if (privateKeyFile == null || privateKeyFile.isEmpty()) {
            throw new IllegalStateException(
                "Private key file not configured. Set via:\n" +
                "  1. Config file (" + DEFAULT_CONFIG_FILE + "): api.privateKeyFile=path/to/key.pem\n" +
                "  2. Environment variable: KALSHI_PRIVATE_KEY_FILE");
        }
    }

    private KalshiApi initializeApi() throws Exception {
        Path privateKeyPath = Path.of(privateKeyFile);
        KalshiAuthenticator authenticator = KalshiAuthenticator.fromFile(apiKeyId, privateKeyPath);

        return KalshiApi.builder()
                .baseUrl(KalshiClient.DEFAULT_BASE_URL)
                .authenticator(authenticator)
                .build();
    }

    private RiskChecker initializeRiskChecker(PositionManager positionManager) {
        RiskConfig config = RiskConfig.builder()
                // Global limits
                .maxOrderQuantity(50)
                .maxOrderNotional(2500)      // $25 max per order
                .maxPositionQuantity(200)
                .maxPositionNotional(10000)  // $100 max per position
                // Strategy-specific overrides
                .forStrategy("IndexEventStrategy")
                    .maxOrderQuantity(25)     // More conservative for index events
                    .maxOrderNotional(1250)
                    .done()
                .build();

        RiskChecker riskChecker = new RiskChecker(config);
        riskChecker.setPositionProvider(positionManager::getPosition);

        return riskChecker;
    }

    private void shutdown(OrderManager orderManager, PositionManager positionManager, MarketManager marketManager) {
        if (orderManager != null) orderManager.shutdown();
        if (positionManager != null) positionManager.stop();
        if (marketManager != null) marketManager.shutdown();
    }

    // ==================== Alternative Usage Examples ====================

    /**
     * Example: Using fluent filter API.
     */
    public EventInterestList exampleFluentFilter(EventFilter filter) {
        return filter.from("KXINX", "KXNDX", "KXBTC")
                .andFrom("KXCPI")
                .minStrikeDateFromNow(Duration.ofHours(1))
                .maxStrikeDateFromNow(Duration.ofDays(7))
                .titleContains("close")
                .where(e -> e.getMutuallyExclusive() != null && e.getMutuallyExclusive())
                .execute();
    }

    /**
     * Example: Filter and limit results.
     */
    public EventInterestList exampleFilterAndLimit(EventFilter filter) {
        EventInterestList all = filter.filter(SERIES_TICKERS, EventFilter.defaultCriteria());

        // Limit to top 10 by strike date
        return all.limit(10);
    }

    /**
     * Example: Category-based filtering.
     */
    public EventInterestList exampleCategoryFilter(EventFilter filter) {
        return filter.filter(SERIES_TICKERS,
                EventFilterCriteria.builder()
                        .category("Financials")
                        .minStrikeDateFromNow(Duration.ofHours(2))
                        .build());
    }

    /**
     * Example: Create strategies with custom configuration per event.
     */
    public void exampleCustomStrategies(StrategyManager manager, EventInterestList interestList) {
        for (Event event : interestList.getEvents()) {
            String ticker = event.getEventTicker();
            String series = event.getSeriesTicker();

            // Create different strategy configurations based on event properties
            EventStrategy strategy;
            if ("KXBTC".equals(series)) {
                // More aggressive strategy for crypto
                strategy = new IndexEventStrategy(ticker) {
                    @Override
                    protected long getTimerIntervalSeconds() {
                        return 5;  // Faster updates for crypto
                    }
                };
            } else {
                // Standard strategy for indices
                strategy = new IndexEventStrategy(ticker);
            }

            manager.addStrategy(strategy);
        }

        manager.initializeAll();
    }
}
