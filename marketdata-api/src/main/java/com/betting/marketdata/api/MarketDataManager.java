package com.betting.marketdata.api;

import com.betting.marketdata.model.Quote;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Interface for managing market data subscriptions and quote retrieval.
 *
 * <p>Implementations of this interface provide access to market data from
 * various sources (e.g., E*TRADE, other brokers, data providers).</p>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * MarketDataManager manager = factory.create(config);
 *
 * // Authenticate if required
 * if (!manager.isAuthenticated()) {
 *     manager.authenticate(authUrl -> promptUserForCode(authUrl));
 * }
 *
 * // Subscribe to quotes
 * String subscriptionId = manager.subscribe(
 *     Arrays.asList("AAPL", "GOOGL"),
 *     quotes -> processQuotes(quotes),
 *     error -> handleError(error)
 * );
 *
 * // Later: cleanup
 * manager.unsubscribe(subscriptionId);
 * manager.shutdown();
 * }</pre>
 */
public interface MarketDataManager {

    /**
     * Get the name/identifier of this market data provider.
     *
     * @return the provider name (e.g., "ETRADE", "KALSHI")
     */
    String getProviderName();

    /**
     * Check if the manager requires authentication.
     *
     * @return true if authentication is required
     */
    boolean requiresAuthentication();

    /**
     * Check if the manager is currently authenticated.
     *
     * @return true if authenticated
     */
    boolean isAuthenticated();

    /**
     * Authenticate with the market data provider.
     *
     * @param authHandler handler for user authorization flow
     * @throws IOException if authentication fails
     */
    void authenticate(AuthorizationHandler authHandler) throws IOException;

    /**
     * Subscribe to quote updates for the given symbols.
     *
     * @param symbols the symbols to subscribe to
     * @param listener the listener to invoke when quotes are received
     * @return a subscription ID that can be used to unsubscribe
     */
    String subscribe(Collection<String> symbols, QuoteListener listener);

    /**
     * Subscribe to quote updates with error handling.
     *
     * @param symbols the symbols to subscribe to
     * @param listener the listener to invoke when quotes are received
     * @param errorListener the listener to invoke on errors (optional)
     * @return a subscription ID that can be used to unsubscribe
     */
    String subscribe(Collection<String> symbols, QuoteListener listener,
                     MarketDataErrorListener errorListener);

    /**
     * Unsubscribe from a specific subscription.
     *
     * @param subscriptionId the subscription ID to cancel
     * @return true if the subscription was found and cancelled
     */
    boolean unsubscribe(String subscriptionId);

    /**
     * Unsubscribe from all active subscriptions.
     */
    void unsubscribeAll();

    /**
     * Get the number of active subscriptions.
     *
     * @return the number of active subscriptions
     */
    int getActiveSubscriptionCount();

    /**
     * Get all active subscription IDs.
     *
     * @return set of active subscription IDs
     */
    Set<String> getActiveSubscriptionIds();

    /**
     * Fetch quotes on-demand (one-time, not subscription-based).
     *
     * @param symbols the symbols to fetch
     * @return list of quotes
     * @throws IOException if the request fails
     */
    List<Quote> getQuotes(Collection<String> symbols) throws IOException;

    /**
     * Fetch quotes on-demand (varargs version).
     *
     * @param symbols the symbols to fetch
     * @return list of quotes
     * @throws IOException if the request fails
     */
    List<Quote> getQuotes(String... symbols) throws IOException;

    /**
     * Get the configured poll interval in milliseconds.
     *
     * @return poll interval in milliseconds
     */
    long getPollIntervalMs();

    /**
     * Check if the manager is running (not shutdown).
     *
     * @return true if running
     */
    boolean isRunning();

    /**
     * Shutdown the manager and release all resources.
     */
    void shutdown();
}
