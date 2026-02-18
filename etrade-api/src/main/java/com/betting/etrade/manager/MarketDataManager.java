package com.betting.etrade.manager;

import com.betting.etrade.client.ETradeClient;
import com.betting.etrade.exception.ETradeApiException;
import com.betting.etrade.model.DetailFlag;
import com.betting.etrade.model.QuoteData;
import com.betting.etrade.oauth.OAuthConfig;
import com.betting.etrade.oauth.OAuthToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MarketDataManager provides a high-level gateway for accessing E*TRADE market data.
 *
 * <p>This class handles OAuth authentication, quote subscriptions, and periodic polling
 * for INTRADAY quotes. Other applications can use this as the single entry point for
 * E*TRADE market data.</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create manager from config file
 * MarketDataManager manager = MarketDataManager.fromPropertiesFile("etrade-config.properties");
 *
 * // Authenticate (interactive OAuth flow)
 * manager.authenticate(authUrl -> {
 *     System.out.println("Visit: " + authUrl);
 *     return promptUserForVerifierCode();
 * });
 *
 * // Subscribe to quotes with callback
 * String subscriptionId = manager.subscribe(
 *     Arrays.asList("AAPL", "GOOGL", "MSFT"),
 *     quotes -> {
 *         for (QuoteData quote : quotes) {
 *             System.out.printf("%s: $%.2f%n", quote.getSymbol(), quote.getLastPrice());
 *         }
 *     }
 * );
 *
 * // Later: unsubscribe and shutdown
 * manager.unsubscribe(subscriptionId);
 * manager.shutdown();
 * }</pre>
 *
 * <h2>Configuration Properties:</h2>
 * <ul>
 *   <li>{@code consumer.key} - E*TRADE API consumer key (required)</li>
 *   <li>{@code consumer.secret} - E*TRADE API consumer secret (required)</li>
 *   <li>{@code use.sandbox} - Use sandbox environment (default: true)</li>
 *   <li>{@code poll.interval.seconds} - Quote polling interval in seconds (default: 5)</li>
 * </ul>
 */
public class MarketDataManager {

    private static final Logger log = LoggerFactory.getLogger(MarketDataManager.class);

    private static final String PROP_CONSUMER_KEY = "consumer.key";
    private static final String PROP_CONSUMER_SECRET = "consumer.secret";
    private static final String PROP_USE_SANDBOX = "use.sandbox";
    private static final String PROP_POLL_INTERVAL = "poll.interval.seconds";

    private static final long DEFAULT_POLL_INTERVAL_SECONDS = 5;

    private final ETradeClient client;
    private final long pollIntervalMs;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Subscription> subscriptions;
    private final AtomicBoolean isShutdown;
    private final AtomicBoolean isAuthenticated;

    private OAuthToken requestToken;

    /**
     * Create a MarketDataManager from a properties file path.
     *
     * @param propertiesFilePath path to the configuration properties file
     * @return a new MarketDataManager instance
     * @throws IOException if the file cannot be read
     */
    public static MarketDataManager fromPropertiesFile(String propertiesFilePath) throws IOException {
        return fromPropertiesFile(Path.of(propertiesFilePath));
    }

    /**
     * Create a MarketDataManager from a properties file path.
     *
     * @param propertiesFilePath path to the configuration properties file
     * @return a new MarketDataManager instance
     * @throws IOException if the file cannot be read
     */
    public static MarketDataManager fromPropertiesFile(Path propertiesFilePath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(propertiesFilePath)) {
            props.load(is);
        }
        return fromProperties(props);
    }

    /**
     * Create a MarketDataManager from a Properties object.
     *
     * @param properties the configuration properties
     * @return a new MarketDataManager instance
     */
    public static MarketDataManager fromProperties(Properties properties) {
        String consumerKey = properties.getProperty(PROP_CONSUMER_KEY);
        String consumerSecret = properties.getProperty(PROP_CONSUMER_SECRET);
        boolean useSandbox = Boolean.parseBoolean(properties.getProperty(PROP_USE_SANDBOX, "true"));
        long pollIntervalSeconds = Long.parseLong(
                properties.getProperty(PROP_POLL_INTERVAL, String.valueOf(DEFAULT_POLL_INTERVAL_SECONDS)));

        if (consumerKey == null || consumerKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required property: " + PROP_CONSUMER_KEY);
        }
        if (consumerSecret == null || consumerSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required property: " + PROP_CONSUMER_SECRET);
        }

        OAuthConfig config = OAuthConfig.builder()
                .consumerKey(consumerKey.trim())
                .consumerSecret(consumerSecret.trim())
                .sandbox(useSandbox)
                .build();

        return new MarketDataManager(config, pollIntervalSeconds);
    }

    /**
     * Create a MarketDataManager with the given configuration.
     *
     * @param config OAuth configuration
     * @param pollIntervalSeconds interval between quote polls in seconds
     */
    public MarketDataManager(OAuthConfig config, long pollIntervalSeconds) {
        this.client = new ETradeClient(config);
        this.pollIntervalMs = pollIntervalSeconds * 1000;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "etrade-market-data-poller");
            t.setDaemon(true);
            return t;
        });
        this.subscriptions = new ConcurrentHashMap<>();
        this.isShutdown = new AtomicBoolean(false);
        this.isAuthenticated = new AtomicBoolean(false);

        log.info("MarketDataManager created - sandbox: {}, poll interval: {}s",
                config.isSandbox(), pollIntervalSeconds);
    }

    /**
     * Authenticate with E*TRADE using the OAuth flow.
     *
     * @param authCallback callback that handles user authorization
     * @throws IOException if authentication fails
     */
    public void authenticate(AuthorizationCallback authCallback) throws IOException {
        if (isAuthenticated.get()) {
            log.info("Already authenticated");
            return;
        }

        log.info("Starting OAuth authentication flow...");

        // Step 1: Get request token
        requestToken = client.getOAuthService().getRequestToken();
        log.debug("Request token obtained");

        // Step 2: Get authorization URL and prompt user
        String authUrl = client.getOAuthService().getAuthorizationUrl(requestToken);
        log.debug("Authorization URL: {}", authUrl);

        // Step 3: Get verifier from user via callback
        String verifier = authCallback.onAuthorizationRequired(authUrl);
        if (verifier == null || verifier.trim().isEmpty()) {
            throw new ETradeApiException("Verification code is required");
        }

        // Step 4: Exchange for access token
        client.completeAuthentication(requestToken, verifier.trim());
        isAuthenticated.set(true);
        log.info("Authentication successful");
    }

    /**
     * Check if the manager is authenticated.
     *
     * @return true if authenticated with E*TRADE
     */
    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    /**
     * Subscribe to INTRADAY quotes for the given symbols.
     *
     * @param symbols the symbols to subscribe to
     * @param callback the callback to invoke when quotes are received
     * @return a subscription ID that can be used to unsubscribe
     * @throws IllegalStateException if not authenticated
     */
    public String subscribe(Collection<String> symbols, QuoteUpdateCallback callback) {
        return subscribe(symbols, callback, null);
    }

    /**
     * Subscribe to INTRADAY quotes for the given symbols with error handling.
     *
     * @param symbols the symbols to subscribe to
     * @param callback the callback to invoke when quotes are received
     * @param errorCallback the callback to invoke on errors (optional)
     * @return a subscription ID that can be used to unsubscribe
     * @throws IllegalStateException if not authenticated or shutdown
     */
    public String subscribe(Collection<String> symbols, QuoteUpdateCallback callback,
                            MarketDataErrorCallback errorCallback) {
        ensureAuthenticated();
        ensureNotShutdown();

        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols cannot be null or empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        String subscriptionId = UUID.randomUUID().toString();
        List<String> symbolList = new ArrayList<>(symbols);

        log.info("Creating subscription {} for symbols: {}", subscriptionId, symbolList);

        // Create polling task
        Runnable pollTask = createPollTask(subscriptionId, symbolList, callback, errorCallback);

        // Schedule at fixed rate
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                pollTask, 0, pollIntervalMs, TimeUnit.MILLISECONDS);

        // Store subscription
        Subscription subscription = new Subscription(subscriptionId, symbolList, callback, errorCallback, future);
        subscriptions.put(subscriptionId, subscription);

        return subscriptionId;
    }

    /**
     * Subscribe to INTRADAY quotes (varargs version).
     *
     * @param callback the callback to invoke when quotes are received
     * @param symbols the symbols to subscribe to
     * @return a subscription ID
     */
    public String subscribe(QuoteUpdateCallback callback, String... symbols) {
        return subscribe(Arrays.asList(symbols), callback);
    }

    /**
     * Unsubscribe from a specific subscription.
     *
     * @param subscriptionId the subscription ID to cancel
     * @return true if the subscription was found and cancelled
     */
    public boolean unsubscribe(String subscriptionId) {
        Subscription subscription = subscriptions.remove(subscriptionId);
        if (subscription != null) {
            log.info("Cancelling subscription: {} for symbols: {}",
                    subscriptionId, subscription.symbols);
            subscription.future.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Unsubscribe from all active subscriptions.
     */
    public void unsubscribeAll() {
        log.info("Cancelling all {} subscriptions", subscriptions.size());
        subscriptions.forEach((id, sub) -> sub.future.cancel(false));
        subscriptions.clear();
    }

    /**
     * Get the number of active subscriptions.
     *
     * @return the number of active subscriptions
     */
    public int getActiveSubscriptionCount() {
        return subscriptions.size();
    }

    /**
     * Get the symbols for a specific subscription.
     *
     * @param subscriptionId the subscription ID
     * @return the list of symbols, or empty list if not found
     */
    public List<String> getSubscriptionSymbols(String subscriptionId) {
        Subscription subscription = subscriptions.get(subscriptionId);
        return subscription != null ? new ArrayList<>(subscription.symbols) : Collections.emptyList();
    }

    /**
     * Get all active subscription IDs.
     *
     * @return set of active subscription IDs
     */
    public Set<String> getActiveSubscriptionIds() {
        return new HashSet<>(subscriptions.keySet());
    }

    /**
     * Fetch quotes on-demand (one-time, not subscription-based).
     *
     * @param symbols the symbols to fetch
     * @return list of quote data
     * @throws IOException if the request fails
     */
    public List<QuoteData> getQuotes(String... symbols) throws IOException {
        ensureAuthenticated();
        return client.getIntradayQuotes(symbols);
    }

    /**
     * Fetch quotes on-demand (one-time, not subscription-based).
     *
     * @param symbols the symbols to fetch
     * @return list of quote data
     * @throws IOException if the request fails
     */
    public List<QuoteData> getQuotes(Collection<String> symbols) throws IOException {
        ensureAuthenticated();
        return client.getIntradayQuotes(symbols);
    }

    /**
     * Get the configured poll interval in milliseconds.
     *
     * @return poll interval in milliseconds
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Check if the manager is running (not shutdown).
     *
     * @return true if running
     */
    public boolean isRunning() {
        return !isShutdown.get();
    }

    /**
     * Shutdown the manager and release all resources.
     * This will cancel all active subscriptions.
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down MarketDataManager");
            unsubscribeAll();
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("MarketDataManager shutdown complete");
        }
    }

    /**
     * Get the underlying ETradeClient (for advanced usage).
     *
     * @return the ETradeClient instance
     */
    public ETradeClient getClient() {
        return client;
    }

    // --- Private methods ---

    private Runnable createPollTask(String subscriptionId, List<String> symbols,
                                    QuoteUpdateCallback callback, MarketDataErrorCallback errorCallback) {
        return () -> {
            try {
                log.debug("Polling quotes for subscription {}: {}", subscriptionId, symbols);
                List<QuoteData> quotes = client.getQuotes(DetailFlag.INTRADAY,
                        symbols.toArray(new String[0]));

                if (!quotes.isEmpty()) {
                    log.debug("Received {} quotes for subscription {}", quotes.size(), subscriptionId);
                    try {
                        callback.onQuoteUpdate(quotes);
                    } catch (Exception e) {
                        log.error("Error in quote callback for subscription {}: {}",
                                subscriptionId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Error fetching quotes for subscription {}: {}", subscriptionId, e.getMessage());
                if (errorCallback != null) {
                    try {
                        errorCallback.onError(e);
                    } catch (Exception callbackError) {
                        log.error("Error in error callback: {}", callbackError.getMessage());
                    }
                }
            }
        };
    }

    private void ensureAuthenticated() {
        if (!isAuthenticated.get()) {
            throw new IllegalStateException("Not authenticated. Call authenticate() first.");
        }
    }

    private void ensureNotShutdown() {
        if (isShutdown.get()) {
            throw new IllegalStateException("MarketDataManager has been shutdown");
        }
    }

    // --- Inner class for subscription tracking ---

    private static class Subscription {
        final String id;
        final List<String> symbols;
        final QuoteUpdateCallback callback;
        final MarketDataErrorCallback errorCallback;
        final ScheduledFuture<?> future;

        Subscription(String id, List<String> symbols, QuoteUpdateCallback callback,
                     MarketDataErrorCallback errorCallback, ScheduledFuture<?> future) {
            this.id = id;
            this.symbols = symbols;
            this.callback = callback;
            this.errorCallback = errorCallback;
            this.future = future;
        }
    }
}
