package com.betting.etrade.subscription;

import com.betting.etrade.client.ETradeClient;
import com.betting.etrade.model.DetailFlag;
import com.betting.etrade.model.QuoteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscription service for polling E*TRADE quotes at regular intervals.
 *
 * <p>This class provides a way to subscribe to quote updates for multiple symbols.
 * It polls the E*TRADE API at configurable intervals and invokes callbacks when
 * new quotes are received.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ETradeClient client = new ETradeClient(config);
 * // ... authenticate client ...
 *
 * QuoteSubscription subscription = new QuoteSubscription(client);
 * subscription.subscribe(
 *     Arrays.asList("AAPL", "GOOGL", "MSFT"),
 *     quotes -> {
 *         for (QuoteData quote : quotes) {
 *             System.out.println(quote.getSymbol() + ": " + quote.getLastPrice());
 *         }
 *     }
 * );
 *
 * // Later, when done:
 * subscription.shutdown();
 * }</pre>
 */
public class QuoteSubscription {

    private static final Logger log = LoggerFactory.getLogger(QuoteSubscription.class);
    private static final long DEFAULT_POLL_INTERVAL_MS = 5000; // 5 seconds

    private final ETradeClient client;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> activeSubscriptions;
    private final AtomicBoolean isShutdown;
    private final DetailFlag detailFlag;
    private final long pollIntervalMs;

    /**
     * Create a new quote subscription with default settings (INTRADAY, 5 second polling).
     */
    public QuoteSubscription(ETradeClient client) {
        this(client, DetailFlag.INTRADAY, DEFAULT_POLL_INTERVAL_MS);
    }

    /**
     * Create a new quote subscription with custom poll interval.
     */
    public QuoteSubscription(ETradeClient client, long pollIntervalMs) {
        this(client, DetailFlag.INTRADAY, pollIntervalMs);
    }

    /**
     * Create a new quote subscription with custom settings.
     */
    public QuoteSubscription(ETradeClient client, DetailFlag detailFlag, long pollIntervalMs) {
        this.client = client;
        this.detailFlag = detailFlag;
        this.pollIntervalMs = pollIntervalMs;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "etrade-quote-poller");
            t.setDaemon(true);
            return t;
        });
        this.activeSubscriptions = new ConcurrentHashMap<>();
        this.isShutdown = new AtomicBoolean(false);
    }

    /**
     * Subscribe to quotes for the given symbols.
     *
     * @param symbols the symbols to subscribe to
     * @param callback the callback to invoke when quotes are received
     * @return a subscription ID that can be used to unsubscribe
     */
    public String subscribe(Collection<String> symbols, QuoteCallback callback) {
        return subscribe(symbols, callback, null);
    }

    /**
     * Subscribe to quotes for the given symbols with error handling.
     *
     * @param symbols the symbols to subscribe to
     * @param callback the callback to invoke when quotes are received
     * @param errorCallback the callback to invoke on errors (optional)
     * @return a subscription ID that can be used to unsubscribe
     */
    public String subscribe(Collection<String> symbols, QuoteCallback callback,
            QuoteErrorCallback errorCallback) {

        if (isShutdown.get()) {
            throw new IllegalStateException("QuoteSubscription has been shut down");
        }

        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols cannot be null or empty");
        }

        if (callback == null) {
            throw new IllegalArgumentException("Callback cannot be null");
        }

        String subscriptionId = UUID.randomUUID().toString();
        List<String> symbolList = new ArrayList<>(symbols);

        log.info("Creating subscription {} for symbols: {}", subscriptionId, symbolList);

        Runnable pollTask = createPollTask(symbolList, callback, errorCallback);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                pollTask, 0, pollIntervalMs, TimeUnit.MILLISECONDS);

        activeSubscriptions.put(subscriptionId, future);

        return subscriptionId;
    }

    /**
     * Subscribe to quotes for the given symbols (varargs version).
     */
    public String subscribe(QuoteCallback callback, String... symbols) {
        return subscribe(Arrays.asList(symbols), callback);
    }

    /**
     * Unsubscribe from a specific subscription.
     */
    public boolean unsubscribe(String subscriptionId) {
        ScheduledFuture<?> future = activeSubscriptions.remove(subscriptionId);
        if (future != null) {
            log.info("Cancelling subscription: {}", subscriptionId);
            future.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Unsubscribe from all active subscriptions.
     */
    public void unsubscribeAll() {
        log.info("Cancelling all {} subscriptions", activeSubscriptions.size());
        activeSubscriptions.forEach((id, future) -> future.cancel(false));
        activeSubscriptions.clear();
    }

    /**
     * Get the number of active subscriptions.
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }

    /**
     * Check if the subscription service is running.
     */
    public boolean isRunning() {
        return !isShutdown.get() && !scheduler.isShutdown();
    }

    /**
     * Shutdown the subscription service.
     * This will cancel all active subscriptions and stop the polling threads.
     */
    public void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            log.info("Shutting down QuoteSubscription");
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
        }
    }

    /**
     * Create the polling task for a subscription.
     */
    private Runnable createPollTask(List<String> symbols, QuoteCallback callback,
            QuoteErrorCallback errorCallback) {

        return () -> {
            try {
                log.debug("Polling quotes for: {}", symbols);
                List<QuoteData> quotes = client.getQuotes(detailFlag,
                        symbols.toArray(new String[0]));

                if (!quotes.isEmpty()) {
                    log.debug("Received {} quotes", quotes.size());
                    callback.onQuotesReceived(quotes);
                }
            } catch (Exception e) {
                log.error("Error fetching quotes for {}: {}", symbols, e.getMessage());
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

    /**
     * Get the poll interval in milliseconds.
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Get the detail flag used for quotes.
     */
    public DetailFlag getDetailFlag() {
        return detailFlag;
    }
}
