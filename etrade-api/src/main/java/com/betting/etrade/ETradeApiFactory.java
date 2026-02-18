package com.betting.etrade;

import com.betting.etrade.client.ETradeClient;
import com.betting.etrade.oauth.OAuthConfig;
import com.betting.etrade.subscription.QuoteSubscription;

/**
 * Factory for creating E*TRADE API clients and subscriptions.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create a sandbox client
 * ETradeClient client = ETradeApiFactory.createSandboxClient(
 *     "your-consumer-key",
 *     "your-consumer-secret"
 * );
 *
 * // Authenticate (interactive flow)
 * String authUrl = client.startAuthentication();
 * System.out.println("Visit: " + authUrl);
 * // User enters verifier code...
 * client.completeAuthentication(requestToken, verifierCode);
 *
 * // Create subscription for INTRADAY quotes
 * QuoteSubscription subscription = ETradeApiFactory.createSubscription(client);
 * subscription.subscribe(
 *     Arrays.asList("AAPL", "GOOGL"),
 *     quotes -> quotes.forEach(q ->
 *         System.out.println(q.getSymbol() + ": $" + q.getLastPrice())
 *     )
 * );
 * }</pre>
 */
public final class ETradeApiFactory {

    private ETradeApiFactory() {
        // Utility class
    }

    /**
     * Create a client for the E*TRADE sandbox environment.
     */
    public static ETradeClient createSandboxClient(String consumerKey, String consumerSecret) {
        OAuthConfig config = OAuthConfig.builder()
                .consumerKey(consumerKey)
                .consumerSecret(consumerSecret)
                .sandbox(true)
                .build();
        return new ETradeClient(config);
    }

    /**
     * Create a client for the E*TRADE live environment.
     */
    public static ETradeClient createLiveClient(String consumerKey, String consumerSecret) {
        OAuthConfig config = OAuthConfig.builder()
                .consumerKey(consumerKey)
                .consumerSecret(consumerSecret)
                .sandbox(false)
                .build();
        return new ETradeClient(config);
    }

    /**
     * Create a client with custom configuration.
     */
    public static ETradeClient createClient(OAuthConfig config) {
        return new ETradeClient(config);
    }

    /**
     * Create a quote subscription with default settings (5 second polling).
     */
    public static QuoteSubscription createSubscription(ETradeClient client) {
        return new QuoteSubscription(client);
    }

    /**
     * Create a quote subscription with custom poll interval.
     *
     * @param client the authenticated E*TRADE client
     * @param pollIntervalMs the interval between quote polls in milliseconds
     */
    public static QuoteSubscription createSubscription(ETradeClient client, long pollIntervalMs) {
        return new QuoteSubscription(client, pollIntervalMs);
    }
}
