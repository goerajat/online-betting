package com.kalshi.client.manager;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.model.Event;
import com.kalshi.client.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages event data with caching and async loading capabilities.
 * Provides centralized access to events with TTL-based cache.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>TTL-based caching of events</li>
 *   <li>Async loading with callbacks</li>
 *   <li>Index events by series ticker</li>
 *   <li>Event change notifications</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * EventManager manager = new EventManager(kalshiApi);
 *
 * // Load events for a series with callback
 * manager.loadEventsForSeries("KXINX", event -> {
 *     System.out.println("Loaded: " + event.getEventTicker());
 *     // Create strategy for this event
 * });
 *
 * // Get cached event
 * Event event = manager.getEvent("KXINX-25JAN15");
 *
 * // Check cache stats
 * System.out.println("Cached events: " + manager.getCachedEventCount());
 * }</pre>
 */
public class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private static final Duration DEFAULT_EVENT_TTL = Duration.ofMinutes(30);
    private static final int DEFAULT_PARALLEL_THREADS = 4;

    private final KalshiApi api;
    private final ExecutorService executor;
    private final Duration eventTtl;

    // Cached events by ticker
    private final ConcurrentHashMap<String, CachedEvent> eventCache = new ConcurrentHashMap<>();

    // Events indexed by series ticker
    private final ConcurrentHashMap<String, Set<String>> eventsBySeriesTicker = new ConcurrentHashMap<>();

    // Track which series are currently loading
    private final Set<String> loadingSeriesTickers = ConcurrentHashMap.newKeySet();

    // Track which series have been fully loaded
    private final Set<String> loadedSeriesTickers = ConcurrentHashMap.newKeySet();

    // Listeners
    private final List<Consumer<EventChangeEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Create an EventManager with default settings.
     *
     * @param api KalshiApi instance
     */
    public EventManager(KalshiApi api) {
        this(api, DEFAULT_EVENT_TTL, DEFAULT_PARALLEL_THREADS);
    }

    /**
     * Create an EventManager with custom settings.
     *
     * @param api KalshiApi instance
     * @param eventTtl TTL for cached events
     * @param parallelThreads Number of parallel threads for loading
     */
    public EventManager(KalshiApi api, Duration eventTtl, int parallelThreads) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.eventTtl = eventTtl != null ? eventTtl : DEFAULT_EVENT_TTL;
        this.executor = Executors.newFixedThreadPool(
                parallelThreads > 0 ? parallelThreads : DEFAULT_PARALLEL_THREADS,
                r -> {
                    Thread t = new Thread(r, "EventManager-Worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    // ==================== Async Loading ====================

    /**
     * Load events for a series asynchronously.
     * The callback is invoked for each event as it's loaded.
     *
     * @param seriesTicker Series ticker to load events for
     * @param onEventLoaded Callback invoked for each event loaded
     */
    public void loadEventsForSeries(String seriesTicker, Consumer<Event> onEventLoaded) {
        loadEventsForSeries(seriesTicker, onEventLoaded, null);
    }

    /**
     * Load events for a series asynchronously.
     * The callback is invoked for each event as it's loaded.
     *
     * @param seriesTicker Series ticker to load events for
     * @param onEventLoaded Callback invoked for each event loaded
     * @param onComplete Callback invoked when loading completes (with count of events loaded)
     */
    public void loadEventsForSeries(String seriesTicker, Consumer<Event> onEventLoaded,
                                     Consumer<LoadResult> onComplete) {
        // Check if already loading
        if (!loadingSeriesTickers.add(seriesTicker)) {
            log.debug("Already loading events for series: {}", seriesTicker);
            return;
        }

        executor.submit(() -> {
            try {
                log.info("Loading events for series: {}", seriesTicker);
                notifyListeners(new EventChangeEvent(EventChangeType.LOADING_STARTED, null, seriesTicker));

                EventService.EventQuery query = EventService.EventQuery.builder()
                        .seriesTicker(seriesTicker)
                        .withNestedMarkets(false)
                        .build();

                List<Event> events = api.events().getAllEvents(query);

                int loadedCount = 0;
                for (Event event : events) {
                    // Cache the event
                    cacheEvent(event);

                    // Index by series ticker
                    eventsBySeriesTicker
                            .computeIfAbsent(seriesTicker, k -> ConcurrentHashMap.newKeySet())
                            .add(event.getEventTicker());

                    loadedCount++;

                    // Invoke callback for each event
                    if (onEventLoaded != null) {
                        try {
                            onEventLoaded.accept(event);
                        } catch (Exception e) {
                            log.error("Error in event callback for {}: {}", event.getEventTicker(), e.getMessage());
                        }
                    }

                    // Notify listeners
                    notifyListeners(new EventChangeEvent(EventChangeType.EVENT_LOADED, event, seriesTicker));
                }

                loadedSeriesTickers.add(seriesTicker);
                log.info("Loaded {} events for series: {}", loadedCount, seriesTicker);

                notifyListeners(new EventChangeEvent(EventChangeType.LOADING_COMPLETED, null, seriesTicker, loadedCount));

                // Invoke completion callback
                if (onComplete != null) {
                    try {
                        onComplete.accept(new LoadResult(seriesTicker, loadedCount, null));
                    } catch (Exception e) {
                        log.error("Error in completion callback for series {}: {}", seriesTicker, e.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Failed to load events for series {}: {}", seriesTicker, e.getMessage());
                notifyListeners(new EventChangeEvent(EventChangeType.ERROR, null, seriesTicker, e.getMessage()));

                if (onComplete != null) {
                    try {
                        onComplete.accept(new LoadResult(seriesTicker, 0, e));
                    } catch (Exception ex) {
                        log.error("Error in completion callback: {}", ex.getMessage());
                    }
                }
            } finally {
                loadingSeriesTickers.remove(seriesTicker);
            }
        });
    }

    /**
     * Load events for multiple series asynchronously.
     * The callback is invoked for each event as it's loaded from any series.
     *
     * @param seriesTickers List of series tickers to load events for
     * @param onEventLoaded Callback invoked for each event loaded
     */
    public void loadEventsForSeries(List<String> seriesTickers, Consumer<Event> onEventLoaded) {
        loadEventsForSeries(seriesTickers, onEventLoaded, null);
    }

    /**
     * Load events for multiple series asynchronously.
     * The callback is invoked for each event as it's loaded from any series.
     *
     * @param seriesTickers List of series tickers to load events for
     * @param onEventLoaded Callback invoked for each event loaded
     * @param onAllComplete Callback invoked when all series have been loaded
     */
    public void loadEventsForSeries(List<String> seriesTickers, Consumer<Event> onEventLoaded,
                                     Runnable onAllComplete) {
        if (seriesTickers == null || seriesTickers.isEmpty()) {
            if (onAllComplete != null) {
                onAllComplete.run();
            }
            return;
        }

        // Track completion of all series
        CountDownLatch latch = new CountDownLatch(seriesTickers.size());

        for (String seriesTicker : seriesTickers) {
            loadEventsForSeries(seriesTicker, onEventLoaded, result -> {
                latch.countDown();
                if (latch.getCount() == 0 && onAllComplete != null) {
                    try {
                        onAllComplete.run();
                    } catch (Exception e) {
                        log.error("Error in completion callback: {}", e.getMessage());
                    }
                }
            });
        }
    }

    // ==================== Cache Access ====================

    /**
     * Get an event from cache.
     *
     * @param eventTicker Event ticker
     * @return Event or null if not cached or expired
     */
    public Event getEvent(String eventTicker) {
        CachedEvent cached = eventCache.get(eventTicker);
        if (cached != null && !cached.isExpired()) {
            return cached.event;
        }
        return null;
    }

    /**
     * Get an event from cache, or fetch it if not cached.
     *
     * @param eventTicker Event ticker
     * @return Event (from cache or freshly fetched)
     */
    public Event getEventOrFetch(String eventTicker) {
        Event cached = getEvent(eventTicker);
        if (cached != null) {
            return cached;
        }

        // Fetch from API
        try {
            Event event = api.events().getEvent(eventTicker);
            if (event != null) {
                cacheEvent(event);
            }
            return event;
        } catch (Exception e) {
            log.error("Failed to fetch event {}: {}", eventTicker, e.getMessage());
            return null;
        }
    }

    /**
     * Get all cached events for a series.
     *
     * @param seriesTicker Series ticker
     * @return List of cached events (may be empty)
     */
    public List<Event> getEventsBySeries(String seriesTicker) {
        Set<String> eventTickers = eventsBySeriesTicker.get(seriesTicker);
        if (eventTickers == null) {
            return Collections.emptyList();
        }

        List<Event> events = new ArrayList<>();
        for (String ticker : eventTickers) {
            Event event = getEvent(ticker);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Check if a series has been fully loaded.
     *
     * @param seriesTicker Series ticker
     * @return true if the series events have been loaded
     */
    public boolean isSeriesLoaded(String seriesTicker) {
        return loadedSeriesTickers.contains(seriesTicker);
    }

    /**
     * Check if a series is currently loading.
     *
     * @param seriesTicker Series ticker
     * @return true if loading is in progress
     */
    public boolean isSeriesLoading(String seriesTicker) {
        return loadingSeriesTickers.contains(seriesTicker);
    }

    /**
     * Get count of cached events.
     */
    public int getCachedEventCount() {
        return eventCache.size();
    }

    /**
     * Get all cached event tickers.
     */
    public Set<String> getCachedEventTickers() {
        return Collections.unmodifiableSet(new HashSet<>(eventCache.keySet()));
    }

    // ==================== Cache Management ====================

    /**
     * Cache an event.
     *
     * @param event Event to cache
     */
    public void cacheEvent(Event event) {
        if (event != null && event.getEventTicker() != null) {
            eventCache.put(event.getEventTicker(), new CachedEvent(event, eventTtl));
        }
    }

    /**
     * Cache multiple events.
     *
     * @param events Events to cache
     */
    public void cacheEvents(List<Event> events) {
        for (Event event : events) {
            cacheEvent(event);
        }
    }

    /**
     * Invalidate a cached event.
     *
     * @param eventTicker Event ticker to invalidate
     */
    public void invalidateEvent(String eventTicker) {
        eventCache.remove(eventTicker);
    }

    /**
     * Invalidate all cached events for a series.
     *
     * @param seriesTicker Series ticker
     */
    public void invalidateSeries(String seriesTicker) {
        Set<String> eventTickers = eventsBySeriesTicker.remove(seriesTicker);
        if (eventTickers != null) {
            for (String ticker : eventTickers) {
                eventCache.remove(ticker);
            }
        }
        loadedSeriesTickers.remove(seriesTicker);
    }

    /**
     * Invalidate all cached events.
     */
    public void invalidateAll() {
        eventCache.clear();
        eventsBySeriesTicker.clear();
        loadedSeriesTickers.clear();
        log.info("Invalidated all cached events");
    }

    /**
     * Remove expired entries from cache.
     */
    public void cleanupExpired() {
        int removed = 0;
        Iterator<Map.Entry<String, CachedEvent>> it = eventCache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired events from cache", removed);
        }
    }

    // ==================== Listeners ====================

    /**
     * Add a listener for event changes.
     *
     * @param listener Consumer to receive EventChangeEvents
     */
    public void addEventChangeListener(Consumer<EventChangeEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    /**
     * Remove an event change listener.
     *
     * @param listener Listener to remove
     */
    public void removeEventChangeListener(Consumer<EventChangeEvent> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(EventChangeEvent event) {
        for (Consumer<EventChangeEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.error("Error in event change listener: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the EventManager and release resources.
     */
    public void shutdown() {
        log.info("Shutting down EventManager");

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        eventCache.clear();
        eventsBySeriesTicker.clear();
        loadingSeriesTickers.clear();
        loadedSeriesTickers.clear();
    }

    // ==================== Inner Classes ====================

    /**
     * Cached event entry with TTL.
     */
    private static class CachedEvent {
        final Event event;
        final Instant expiresAt;

        CachedEvent(Event event, Duration ttl) {
            this.event = event;
            this.expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Result of a load operation.
     */
    public static class LoadResult {
        private final String seriesTicker;
        private final int eventsLoaded;
        private final Exception error;

        public LoadResult(String seriesTicker, int eventsLoaded, Exception error) {
            this.seriesTicker = seriesTicker;
            this.eventsLoaded = eventsLoaded;
            this.error = error;
        }

        public String getSeriesTicker() {
            return seriesTicker;
        }

        public int getEventsLoaded() {
            return eventsLoaded;
        }

        public Exception getError() {
            return error;
        }

        public boolean isSuccess() {
            return error == null;
        }

        @Override
        public String toString() {
            if (error != null) {
                return "LoadResult{series='" + seriesTicker + "', error='" + error.getMessage() + "'}";
            }
            return "LoadResult{series='" + seriesTicker + "', loaded=" + eventsLoaded + "}";
        }
    }

    /**
     * Types of event changes.
     */
    public enum EventChangeType {
        LOADING_STARTED,    // Started loading events for a series
        EVENT_LOADED,       // An event was loaded and cached
        LOADING_COMPLETED,  // Finished loading all events for a series
        ERROR               // Error occurred during loading
    }

    /**
     * Event representing an event change.
     */
    public static class EventChangeEvent {
        private final EventChangeType type;
        private final Event event;
        private final String seriesTicker;
        private final String errorMessage;
        private final int count;

        public EventChangeEvent(EventChangeType type, Event event, String seriesTicker) {
            this(type, event, seriesTicker, null, 0);
        }

        public EventChangeEvent(EventChangeType type, Event event, String seriesTicker, int count) {
            this(type, event, seriesTicker, null, count);
        }

        public EventChangeEvent(EventChangeType type, Event event, String seriesTicker, String errorMessage) {
            this(type, event, seriesTicker, errorMessage, 0);
        }

        public EventChangeEvent(EventChangeType type, Event event, String seriesTicker,
                                String errorMessage, int count) {
            this.type = type;
            this.event = event;
            this.seriesTicker = seriesTicker;
            this.errorMessage = errorMessage;
            this.count = count;
        }

        public EventChangeType getType() {
            return type;
        }

        public Event getEvent() {
            return event;
        }

        public String getSeriesTicker() {
            return seriesTicker;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public int getCount() {
            return count;
        }

        @Override
        public String toString() {
            switch (type) {
                case ERROR:
                    return "EventChangeEvent{type=ERROR, series='" + seriesTicker +
                            "', message='" + errorMessage + "'}";
                case EVENT_LOADED:
                    return "EventChangeEvent{type=EVENT_LOADED, event='" +
                            (event != null ? event.getEventTicker() : "null") + "'}";
                case LOADING_COMPLETED:
                    return "EventChangeEvent{type=LOADING_COMPLETED, series='" + seriesTicker +
                            "', count=" + count + "}";
                default:
                    return "EventChangeEvent{type=" + type + ", series='" + seriesTicker + "'}";
            }
        }
    }
}
