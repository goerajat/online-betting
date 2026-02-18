package com.betting.etrade.adapter;

import com.betting.etrade.manager.MarketDataManager;
import com.betting.etrade.model.QuoteData;
import com.betting.marketdata.api.AuthorizationHandler;
import com.betting.marketdata.api.MarketDataErrorListener;
import com.betting.marketdata.api.QuoteListener;
import com.betting.marketdata.model.Quote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapter that wraps the E*TRADE MarketDataManager to implement
 * the generic MarketDataManager interface.
 *
 * <p>This adapter consolidates subscriptions so that multiple listeners
 * can subscribe to the same symbols without causing duplicate API calls.
 * The adapter maintains a single poll loop that fetches all subscribed
 * symbols once per interval and distributes quotes to all registered listeners.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *   <li>Multiple listeners can subscribe to the same ticker</li>
 *   <li>Each symbol is polled only once per interval, regardless of listener count</li>
 *   <li>Quotes are distributed to all listeners subscribed to each symbol</li>
 *   <li>Efficient polling - only fetches symbols that have active listeners</li>
 * </ul>
 */
public class ETradeMarketDataManager implements com.betting.marketdata.api.MarketDataManager {

    private static final Logger log = LoggerFactory.getLogger(ETradeMarketDataManager.class);
    private static final String PROVIDER_NAME = "ETRADE";

    private final MarketDataManager delegate;

    // Consolidated subscription management
    private final Map<String, SubscriptionInfo> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> symbolToSubscriptions = new ConcurrentHashMap<>();
    private final Set<String> allSymbols = ConcurrentHashMap.newKeySet();

    // Single poll task management
    private final ScheduledExecutorService pollScheduler;
    private ScheduledFuture<?> pollTask;
    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private final Object pollLock = new Object();

    public ETradeMarketDataManager(MarketDataManager delegate) {
        this.delegate = delegate;
        this.pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "etrade-consolidated-poller");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public boolean isAuthenticated() {
        return delegate.isAuthenticated();
    }

    @Override
    public void authenticate(AuthorizationHandler authHandler) throws IOException {
        delegate.authenticate(authUrl -> authHandler.handleAuthorization(authUrl));
    }

    @Override
    public String subscribe(Collection<String> symbols, QuoteListener listener) {
        return subscribe(symbols, listener, null);
    }

    @Override
    public String subscribe(Collection<String> symbols, QuoteListener listener,
                            MarketDataErrorListener errorListener) {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("Symbols cannot be null or empty");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }

        // Normalize symbols to uppercase
        Set<String> normalizedSymbols = new HashSet<>();
        for (String symbol : symbols) {
            normalizedSymbols.add(symbol.toUpperCase().trim());
        }

        // Create subscription
        String subscriptionId = UUID.randomUUID().toString();
        SubscriptionInfo info = new SubscriptionInfo(subscriptionId, normalizedSymbols, listener, errorListener);
        subscriptions.put(subscriptionId, info);

        // Track symbol -> subscription mappings
        for (String symbol : normalizedSymbols) {
            symbolToSubscriptions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet()).add(subscriptionId);
            allSymbols.add(symbol);
        }

        log.info("Created subscription {} for {} symbols: {}", subscriptionId, normalizedSymbols.size(), normalizedSymbols);
        log.debug("Total unique symbols now tracked: {}", allSymbols.size());

        // Start polling if not already running
        startPollingIfNeeded();

        return subscriptionId;
    }

    @Override
    public boolean unsubscribe(String subscriptionId) {
        SubscriptionInfo info = subscriptions.remove(subscriptionId);
        if (info == null) {
            return false;
        }

        log.info("Removing subscription {} for symbols: {}", subscriptionId, info.symbols);

        // Remove from symbol mappings
        for (String symbol : info.symbols) {
            Set<String> subs = symbolToSubscriptions.get(symbol);
            if (subs != null) {
                subs.remove(subscriptionId);
                // If no more subscriptions for this symbol, remove it
                if (subs.isEmpty()) {
                    symbolToSubscriptions.remove(symbol);
                    allSymbols.remove(symbol);
                    log.debug("No more listeners for symbol {}, removed from tracking", symbol);
                }
            }
        }

        // Stop polling if no more subscriptions
        stopPollingIfEmpty();

        return true;
    }

    @Override
    public void unsubscribeAll() {
        log.info("Unsubscribing all {} subscriptions", subscriptions.size());

        subscriptions.clear();
        symbolToSubscriptions.clear();
        allSymbols.clear();

        stopPolling();
    }

    @Override
    public int getActiveSubscriptionCount() {
        return subscriptions.size();
    }

    @Override
    public Set<String> getActiveSubscriptionIds() {
        return new HashSet<>(subscriptions.keySet());
    }

    @Override
    public List<Quote> getQuotes(Collection<String> symbols) throws IOException {
        List<QuoteData> quoteDataList = delegate.getQuotes(symbols);
        return ETradeQuoteAdapter.toQuotes(quoteDataList);
    }

    @Override
    public List<Quote> getQuotes(String... symbols) throws IOException {
        return getQuotes(Arrays.asList(symbols));
    }

    @Override
    public long getPollIntervalMs() {
        return delegate.getPollIntervalMs();
    }

    @Override
    public boolean isRunning() {
        return delegate.isRunning();
    }

    @Override
    public void shutdown() {
        log.info("Shutting down ETradeMarketDataManager");
        unsubscribeAll();
        pollScheduler.shutdown();
        try {
            if (!pollScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                pollScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            pollScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        delegate.shutdown();
    }

    /**
     * Get the underlying E*TRADE MarketDataManager.
     */
    public MarketDataManager getDelegate() {
        return delegate;
    }

    /**
     * Get the number of unique symbols being tracked.
     */
    public int getTrackedSymbolCount() {
        return allSymbols.size();
    }

    /**
     * Get all symbols currently being tracked.
     */
    public Set<String> getTrackedSymbols() {
        return new HashSet<>(allSymbols);
    }

    /**
     * Get the number of listeners for a specific symbol.
     */
    public int getListenerCount(String symbol) {
        Set<String> subs = symbolToSubscriptions.get(symbol.toUpperCase().trim());
        return subs != null ? subs.size() : 0;
    }

    // --- Private methods ---

    private void startPollingIfNeeded() {
        synchronized (pollLock) {
            if (!isPolling.get() && !allSymbols.isEmpty()) {
                long intervalMs = delegate.getPollIntervalMs();
                log.info("Starting consolidated poll task with interval {}ms for {} symbols",
                        intervalMs, allSymbols.size());

                pollTask = pollScheduler.scheduleAtFixedRate(
                        this::pollAndDistribute,
                        0,
                        intervalMs,
                        TimeUnit.MILLISECONDS
                );
                isPolling.set(true);
            }
        }
    }

    private void stopPollingIfEmpty() {
        synchronized (pollLock) {
            if (allSymbols.isEmpty()) {
                stopPolling();
            }
        }
    }

    private void stopPolling() {
        synchronized (pollLock) {
            if (pollTask != null && !pollTask.isCancelled()) {
                log.info("Stopping consolidated poll task");
                pollTask.cancel(false);
                pollTask = null;
            }
            isPolling.set(false);
        }
    }

    /**
     * Single poll task that fetches all symbols and distributes to listeners.
     */
    private void pollAndDistribute() {
        if (allSymbols.isEmpty()) {
            return;
        }

        try {
            // Get current symbols to poll
            List<String> symbolsToFetch = new ArrayList<>(allSymbols);
            log.debug("Polling {} symbols: {}", symbolsToFetch.size(), symbolsToFetch);

            // Fetch quotes from E*TRADE (single API call)
            List<QuoteData> quoteDataList = delegate.getQuotes(symbolsToFetch);

            if (quoteDataList.isEmpty()) {
                log.debug("No quotes returned from E*TRADE");
                return;
            }

            // Convert to generic Quote objects
            List<Quote> allQuotes = ETradeQuoteAdapter.toQuotes(quoteDataList);

            // Build symbol -> quote map for efficient lookup
            Map<String, Quote> quoteMap = new HashMap<>();
            for (Quote quote : allQuotes) {
                quoteMap.put(quote.getSymbol().toUpperCase(), quote);
            }

            log.debug("Received {} quotes, distributing to {} subscriptions",
                    allQuotes.size(), subscriptions.size());

            // Distribute quotes to each subscription based on their subscribed symbols
            for (SubscriptionInfo sub : subscriptions.values()) {
                List<Quote> quotesForSubscription = new ArrayList<>();

                for (String symbol : sub.symbols) {
                    Quote quote = quoteMap.get(symbol);
                    if (quote != null) {
                        quotesForSubscription.add(quote);
                    }
                }

                if (!quotesForSubscription.isEmpty()) {
                    try {
                        sub.listener.onQuotes(quotesForSubscription);
                    } catch (Exception e) {
                        log.error("Error in listener callback for subscription {}: {}",
                                sub.id, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error in consolidated poll: {}", e.getMessage());

            // Notify all subscriptions with error listeners
            for (SubscriptionInfo sub : subscriptions.values()) {
                if (sub.errorListener != null) {
                    try {
                        sub.errorListener.onError(e);
                    } catch (Exception callbackError) {
                        log.error("Error in error callback for subscription {}: {}",
                                sub.id, callbackError.getMessage());
                    }
                }
            }
        }
    }

    // --- Inner class for subscription tracking ---

    private static class SubscriptionInfo {
        final String id;
        final Set<String> symbols;
        final QuoteListener listener;
        final MarketDataErrorListener errorListener;

        SubscriptionInfo(String id, Set<String> symbols, QuoteListener listener,
                         MarketDataErrorListener errorListener) {
            this.id = id;
            this.symbols = symbols;
            this.listener = listener;
            this.errorListener = errorListener;
        }
    }
}
