package com.kalshi.sample.marketdata;

import com.betting.etrade.manager.JavaFxAuthorizationDialog;
import com.betting.marketdata.api.AuthorizationHandler;
import com.betting.marketdata.api.MarketDataErrorListener;
import com.betting.marketdata.api.MarketDataManager;
import com.betting.marketdata.api.QuoteListener;
import com.betting.marketdata.factory.ConfigurableMarketDataManagerFactory;
import com.betting.marketdata.model.Quote;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central service for managing market data from external providers.
 *
 * <p>This service uses the ConfigurableMarketDataManagerFactory to create
 * MarketDataManager instances based on configuration. It provides a unified
 * interface for strategies to access market data from multiple providers.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Initialize the service
 * MarketDataService service = MarketDataService.getInstance();
 * service.initialize("marketdata.properties", primaryStage);
 *
 * // Subscribe to quotes
 * service.subscribe(Arrays.asList("SPY", "QQQ"), quotes -> {
 *     for (Quote quote : quotes) {
 *         System.out.println(quote.getSymbol() + ": " + quote.getLastPrice());
 *     }
 * });
 *
 * // Get quotes on-demand
 * List<Quote> quotes = service.getQuotes("AAPL", "MSFT");
 *
 * // Shutdown
 * service.shutdown();
 * }</pre>
 */
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final MarketDataService INSTANCE = new MarketDataService();

    /** Property key for auth mode: "ui" or "cli" */
    public static final String AUTH_MODE_PROPERTY = "auth.mode";

    /** Property key for auto-authenticate on startup: "true" or "false" */
    public static final String AUTO_AUTH_PROPERTY = "auth.auto";

    private final Map<String, MarketDataManager> managers = new ConcurrentHashMap<>();
    private final Map<String, List<QuoteListener>> globalListeners = new ConcurrentHashMap<>();
    private final List<Consumer<MarketDataManager>> managerListeners = new CopyOnWriteArrayList<>();

    private Stage ownerStage;
    private boolean initialized = false;
    private String activeProvider;
    private String authMode = "ui";  // default to UI mode
    private boolean autoAuth = true; // default to auto-authenticate
    private Properties configProperties;

    private MarketDataService() {
        // Singleton
    }

    /**
     * Get the singleton instance.
     */
    public static MarketDataService getInstance() {
        return INSTANCE;
    }

    /**
     * Initialize the service from a properties file.
     *
     * @param propertiesFilePath path to the market data configuration file
     * @param ownerStage JavaFX stage for authorization dialogs (can be null)
     * @throws IOException if initialization fails
     */
    public void initialize(String propertiesFilePath, Stage ownerStage) throws IOException {
        initialize(Path.of(propertiesFilePath), ownerStage);
    }

    /**
     * Initialize the service from a properties file.
     *
     * @param propertiesFilePath path to the market data configuration file
     * @param ownerStage JavaFX stage for authorization dialogs (can be null)
     * @throws IOException if initialization fails
     */
    public void initialize(Path propertiesFilePath, Stage ownerStage) throws IOException {
        if (!Files.exists(propertiesFilePath)) {
            log.warn("Market data config file not found: {}", propertiesFilePath);
            return;
        }

        this.ownerStage = ownerStage;

        Properties props = new Properties();
        props.load(Files.newInputStream(propertiesFilePath));

        initialize(props, ownerStage);
    }

    /**
     * Initialize the service from properties.
     *
     * @param properties the configuration properties
     * @param ownerStage JavaFX stage for authorization dialogs (can be null)
     */
    public void initialize(Properties properties, Stage ownerStage) {
        this.ownerStage = ownerStage;
        this.configProperties = properties;

        // Read auth configuration
        this.authMode = properties.getProperty(AUTH_MODE_PROPERTY, "ui").toLowerCase();
        this.autoAuth = Boolean.parseBoolean(properties.getProperty(AUTO_AUTH_PROPERTY, "true"));

        log.info("Auth mode: {}, Auto-authenticate: {}", authMode, autoAuth);

        String provider = properties.getProperty(ConfigurableMarketDataManagerFactory.PROVIDER_PROPERTY);
        if (provider == null || provider.trim().isEmpty()) {
            log.info("No market data provider configured");
            return;
        }

        try {
            MarketDataManager manager = ConfigurableMarketDataManagerFactory.getInstance().create(properties);
            registerManager(manager);
            activeProvider = manager.getProviderName();
            initialized = true;
            log.info("MarketDataService initialized with provider: {}", activeProvider);

            // Notify listeners
            for (Consumer<MarketDataManager> listener : managerListeners) {
                listener.accept(manager);
            }

        } catch (Exception e) {
            log.error("Failed to initialize market data provider: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the configured authentication mode.
     * @return "ui" for UI dialog mode, "cli" for command-line mode
     */
    public String getAuthMode() {
        return authMode;
    }

    /**
     * Check if auto-authentication is enabled.
     */
    public boolean isAutoAuthEnabled() {
        return autoAuth;
    }

    /**
     * Set the owner stage for UI dialogs.
     * This should be called from the JavaFX Application Thread after the stage is available.
     */
    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    /**
     * Register a MarketDataManager instance.
     */
    public void registerManager(MarketDataManager manager) {
        String providerName = manager.getProviderName().toUpperCase();
        managers.put(providerName, manager);
        log.info("Registered MarketDataManager: {}", providerName);
    }

    /**
     * Get a MarketDataManager by provider name.
     */
    public MarketDataManager getManager(String providerName) {
        return managers.get(providerName.toUpperCase());
    }

    /**
     * Get the active (default) MarketDataManager.
     */
    public MarketDataManager getActiveManager() {
        return activeProvider != null ? managers.get(activeProvider) : null;
    }

    /**
     * Check if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if the active manager is authenticated.
     */
    public boolean isAuthenticated() {
        MarketDataManager manager = getActiveManager();
        return manager != null && manager.isAuthenticated();
    }

    /**
     * Authenticate the active manager using configured auth mode.
     * Uses UI dialog if auth.mode=ui and ownerStage is set, otherwise CLI.
     */
    public void authenticate() throws IOException {
        MarketDataManager manager = getActiveManager();
        if (manager == null) {
            throw new IllegalStateException("No active market data manager");
        }

        if (manager.isAuthenticated()) {
            log.info("Already authenticated with {}", manager.getProviderName());
            return;
        }

        AuthorizationHandler handler = createAuthorizationHandler();
        manager.authenticate(handler);
        log.info("Authenticated with {}", manager.getProviderName());
    }

    /**
     * Creates the appropriate AuthorizationHandler based on configuration.
     */
    private AuthorizationHandler createAuthorizationHandler() {
        if ("cli".equals(authMode)) {
            log.info("Using CLI authentication mode");
            return authUrl -> {
                System.out.println("\n=== E*TRADE Authorization Required ===");
                System.out.println("Visit the following URL to authorize:");
                System.out.println(authUrl);
                System.out.print("\nEnter verification code: ");
                return new Scanner(System.in).nextLine().trim();
            };
        }

        // UI mode
        if (ownerStage != null) {
            log.info("Using UI authentication mode with owner stage");
            return new JavaFxAuthorizationDialog(ownerStage);
        }

        // UI mode but no stage available - use a standalone dialog
        log.info("Using UI authentication mode with standalone dialog");
        return new JavaFxAuthorizationDialog(null);
    }

    /**
     * Subscribe to quotes from the active provider.
     *
     * @param symbols the symbols to subscribe to
     * @param listener the listener for quote updates
     * @return subscription ID
     */
    public String subscribe(Collection<String> symbols, QuoteListener listener) {
        return subscribe(symbols, listener, null);
    }

    /**
     * Subscribe to quotes from the active provider with error handling.
     *
     * @param symbols the symbols to subscribe to
     * @param listener the listener for quote updates
     * @param errorListener the error listener (optional)
     * @return subscription ID
     */
    public String subscribe(Collection<String> symbols, QuoteListener listener,
                            MarketDataErrorListener errorListener) {
        MarketDataManager manager = getActiveManager();
        if (manager == null) {
            throw new IllegalStateException("No active market data manager");
        }
        return manager.subscribe(symbols, listener, errorListener);
    }

    /**
     * Unsubscribe from a subscription.
     */
    public boolean unsubscribe(String subscriptionId) {
        MarketDataManager manager = getActiveManager();
        return manager != null && manager.unsubscribe(subscriptionId);
    }

    /**
     * Get quotes on-demand.
     */
    public List<Quote> getQuotes(String... symbols) throws IOException {
        return getQuotes(Arrays.asList(symbols));
    }

    /**
     * Get quotes on-demand.
     */
    public List<Quote> getQuotes(Collection<String> symbols) throws IOException {
        MarketDataManager manager = getActiveManager();
        if (manager == null) {
            return Collections.emptyList();
        }
        return manager.getQuotes(symbols);
    }

    /**
     * Add a listener for when managers are registered.
     */
    public void addManagerListener(Consumer<MarketDataManager> listener) {
        managerListeners.add(listener);
        // Notify about existing managers
        for (MarketDataManager manager : managers.values()) {
            listener.accept(manager);
        }
    }

    /**
     * Remove a manager listener.
     */
    public void removeManagerListener(Consumer<MarketDataManager> listener) {
        managerListeners.remove(listener);
    }

    /**
     * Get all registered provider names.
     */
    public Set<String> getRegisteredProviders() {
        return new HashSet<>(managers.keySet());
    }

    /**
     * Shutdown all managers.
     */
    public void shutdown() {
        log.info("Shutting down MarketDataService");
        for (MarketDataManager manager : managers.values()) {
            try {
                manager.shutdown();
            } catch (Exception e) {
                log.error("Error shutting down {}: {}", manager.getProviderName(), e.getMessage());
            }
        }
        managers.clear();
        initialized = false;
        activeProvider = null;
    }
}
