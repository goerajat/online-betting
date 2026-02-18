package com.kalshi.client.filter;

import com.kalshi.client.KalshiApi;
import com.kalshi.client.manager.EventManager;
import com.kalshi.client.model.Event;
import com.kalshi.client.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Component for filtering events from multiple series to create an interest list.
 * Supports both synchronous and asynchronous filtering with callbacks.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Fetches events from multiple series tickers</li>
 *   <li>Applies configurable filtering criteria</li>
 *   <li>Supports parallel fetching for better performance</li>
 *   <li>Async filtering with per-event callbacks for immediate processing</li>
 *   <li>Integrates with EventManager for caching</li>
 * </ul>
 *
 * <p>Async Usage (recommended):</p>
 * <pre>{@code
 * EventFilter filter = new EventFilter(kalshiApi);
 * EventFilterCriteria criteria = EventFilterCriteria.builder()
 *     .minStrikeDateFromNow(Duration.ofHours(2))
 *     .maxStrikeDateFromNow(Duration.ofDays(7))
 *     .build();
 *
 * // Async filtering with callback per event
 * filter.filterAsync(seriesTickers, criteria,
 *     event -> {
 *         // Called for each matching event as it loads
 *         createStrategy(event);
 *     },
 *     interestList -> {
 *         // Called when all events are loaded
 *         System.out.println("Loaded " + interestList.size() + " events");
 *     }
 * );
 * }</pre>
 *
 * <p>Sync Usage:</p>
 * <pre>{@code
 * EventInterestList interestList = filter.filter(seriesTickers, criteria);
 * for (String eventTicker : interestList.getEventTickers()) {
 *     createStrategy(eventTicker);
 * }
 * }</pre>
 */
public class EventFilter {

    private static final Logger log = LoggerFactory.getLogger(EventFilter.class);
    private static final int DEFAULT_PARALLEL_THREADS = 4;

    private final KalshiApi api;
    private final EventManager eventManager;
    private final int parallelThreads;
    private final ExecutorService executor;

    /**
     * Create an EventFilter with default settings.
     *
     * @param api KalshiApi instance
     */
    public EventFilter(KalshiApi api) {
        this(api, null, DEFAULT_PARALLEL_THREADS);
    }

    /**
     * Create an EventFilter with EventManager for caching.
     *
     * @param api KalshiApi instance
     * @param eventManager EventManager for caching (optional)
     */
    public EventFilter(KalshiApi api, EventManager eventManager) {
        this(api, eventManager, DEFAULT_PARALLEL_THREADS);
    }

    /**
     * Create an EventFilter with custom settings.
     *
     * @param api KalshiApi instance
     * @param eventManager EventManager for caching (optional)
     * @param parallelThreads Number of threads for parallel fetching
     */
    public EventFilter(KalshiApi api, EventManager eventManager, int parallelThreads) {
        this.api = Objects.requireNonNull(api, "api must not be null");
        this.eventManager = eventManager;
        this.parallelThreads = parallelThreads > 0 ? parallelThreads : DEFAULT_PARALLEL_THREADS;
        this.executor = Executors.newFixedThreadPool(this.parallelThreads, r -> {
            Thread t = new Thread(r, "EventFilter-Worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Create a builder for EventFilter.
     *
     * @param api KalshiApi instance
     * @return Builder
     */
    public static Builder builder(KalshiApi api) {
        return new Builder(api);
    }

    // ==================== Async Filtering Methods ====================

    /**
     * Filter events asynchronously with callbacks.
     * The onEventMatched callback is invoked for each matching event as it loads.
     *
     * @param seriesTicker Series ticker to fetch events from
     * @param criteria Filter criteria
     * @param onEventMatched Callback invoked for each matching event
     */
    public void filterAsync(String seriesTicker, EventFilterCriteria criteria,
                            Consumer<Event> onEventMatched) {
        filterAsync(Collections.singletonList(seriesTicker), criteria, onEventMatched, null);
    }

    /**
     * Filter events asynchronously with callbacks.
     * The onEventMatched callback is invoked for each matching event as it loads.
     *
     * @param seriesTicker Series ticker to fetch events from
     * @param criteria Filter criteria
     * @param onEventMatched Callback invoked for each matching event
     * @param onComplete Callback invoked when filtering completes
     */
    public void filterAsync(String seriesTicker, EventFilterCriteria criteria,
                            Consumer<Event> onEventMatched,
                            Consumer<EventInterestList> onComplete) {
        filterAsync(Collections.singletonList(seriesTicker), criteria, onEventMatched, onComplete);
    }

    /**
     * Filter events from multiple series asynchronously.
     * The onEventMatched callback is invoked for each matching event as it loads.
     *
     * @param seriesTickers Series tickers to fetch events from
     * @param criteria Filter criteria
     * @param onEventMatched Callback invoked for each matching event (can be null)
     * @param onComplete Callback invoked when all filtering completes (can be null)
     */
    public void filterAsync(List<String> seriesTickers, EventFilterCriteria criteria,
                            Consumer<Event> onEventMatched,
                            Consumer<EventInterestList> onComplete) {
        if (seriesTickers == null || seriesTickers.isEmpty()) {
            log.warn("No series tickers provided");
            if (onComplete != null) {
                onComplete.accept(EventInterestList.empty());
            }
            return;
        }

        EventFilterCriteria effectiveCriteria = criteria != null ? criteria : EventFilterCriteria.acceptAll();
        log.info("Starting async filtering from {} series with criteria: {}", seriesTickers.size(), effectiveCriteria);

        // Track all matched events for final result
        List<Event> allMatchedEvents = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(seriesTickers.size());

        for (String seriesTicker : seriesTickers) {
            executor.submit(() -> {
                try {
                    fetchAndFilterSeriesAsync(seriesTicker, effectiveCriteria, event -> {
                        allMatchedEvents.add(event);
                        if (onEventMatched != null) {
                            try {
                                onEventMatched.accept(event);
                            } catch (Exception e) {
                                log.error("Error in event callback for {}: {}",
                                        event.getEventTicker(), e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    log.error("Error filtering series {}: {}", seriesTicker, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion in background and invoke onComplete
        if (onComplete != null) {
            executor.submit(() -> {
                try {
                    latch.await();
                    log.info("Async filtering complete: {} events matched from {} series",
                            allMatchedEvents.size(), seriesTickers.size());
                    onComplete.accept(new EventInterestList(allMatchedEvents));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for filter completion");
                    onComplete.accept(new EventInterestList(allMatchedEvents));
                }
            });
        }
    }

    /**
     * Fetch and filter events for a single series, calling the callback for each match.
     */
    private void fetchAndFilterSeriesAsync(String seriesTicker, EventFilterCriteria criteria,
                                           Consumer<Event> onMatch) {
        log.debug("Fetching events for series: {}", seriesTicker);

        try {
            EventService.EventQuery query = EventService.EventQuery.builder()
                    .seriesTicker(seriesTicker)
                    .withNestedMarkets(false)
                    .build();

            List<Event> events = api.events().getAllEvents(query);
            log.debug("Fetched {} events for series: {}", events.size(), seriesTicker);

            int matchCount = 0;
            for (Event event : events) {
                // Cache in EventManager if available
                if (eventManager != null) {
                    eventManager.cacheEvent(event);
                }

                // Check if event matches criteria
                if (criteria.matches(event)) {
                    matchCount++;
                    onMatch.accept(event);
                }
            }

            log.info("Series {} filtering complete: {} matches from {} events",
                    seriesTicker, matchCount, events.size());

        } catch (Exception e) {
            log.error("Failed to fetch events for series {}: {}", seriesTicker, e.getMessage());
        }
    }

    // ==================== Synchronous Filtering Methods ====================

    /**
     * Filter events from a single series (synchronous).
     *
     * @param seriesTicker Series ticker to fetch events from
     * @param criteria Filter criteria
     * @return EventInterestList with matching events
     */
    public EventInterestList filter(String seriesTicker, EventFilterCriteria criteria) {
        return filter(Collections.singletonList(seriesTicker), criteria);
    }

    /**
     * Filter events from multiple series (synchronous).
     *
     * @param seriesTickers Series tickers to fetch events from
     * @param criteria Filter criteria
     * @return EventInterestList with matching events
     */
    public EventInterestList filter(List<String> seriesTickers, EventFilterCriteria criteria) {
        if (seriesTickers == null || seriesTickers.isEmpty()) {
            log.warn("No series tickers provided");
            return EventInterestList.empty();
        }

        EventFilterCriteria effectiveCriteria = criteria != null ? criteria : EventFilterCriteria.acceptAll();

        log.info("Filtering events from {} series with criteria: {}", seriesTickers.size(), effectiveCriteria);

        // Fetch events from all series
        List<Event> allEvents = fetchEventsFromSeries(seriesTickers);

        // Apply filter criteria
        List<Event> filteredEvents = allEvents.stream()
                .filter(effectiveCriteria::matches)
                .collect(Collectors.toList());

        log.info("Filtered {} events from {} total events across {} series",
                filteredEvents.size(), allEvents.size(), seriesTickers.size());

        return new EventInterestList(filteredEvents);
    }

    /**
     * Filter events from multiple series using default criteria (accept all).
     *
     * @param seriesTickers Series tickers to fetch events from
     * @return EventInterestList with all events from the series
     */
    public EventInterestList filter(List<String> seriesTickers) {
        return filter(seriesTickers, EventFilterCriteria.acceptAll());
    }

    /**
     * Filter events using a fluent builder pattern.
     *
     * @return FilterBuilder for fluent filtering
     */
    public FilterBuilder from(String... seriesTickers) {
        return new FilterBuilder(this, Arrays.asList(seriesTickers));
    }

    /**
     * Filter events using a fluent builder pattern.
     *
     * @return FilterBuilder for fluent filtering
     */
    public FilterBuilder from(List<String> seriesTickers) {
        return new FilterBuilder(this, seriesTickers);
    }

    // ==================== Fetching Logic ====================

    /**
     * Fetch events from multiple series (parallelized).
     */
    private List<Event> fetchEventsFromSeries(List<String> seriesTickers) {
        if (seriesTickers.size() == 1) {
            // Single series - no need for parallelization
            return fetchEventsFromSingleSeries(seriesTickers.get(0));
        }

        // Multiple series - parallelize fetching
        List<Future<List<Event>>> futures = new ArrayList<>();

        for (String seriesTicker : seriesTickers) {
            futures.add(executor.submit(() -> fetchEventsFromSingleSeries(seriesTicker)));
        }

        List<Event> allEvents = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                List<Event> events = futures.get(i).get();
                allEvents.addAll(events);
            } catch (ExecutionException e) {
                log.error("Error fetching events for series {}: {}",
                        seriesTickers.get(i), e.getCause().getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while fetching events");
                break;
            }
        }

        return allEvents;
    }

    /**
     * Fetch events from a single series (with auto-pagination).
     */
    private List<Event> fetchEventsFromSingleSeries(String seriesTicker) {
        log.debug("Fetching events for series: {}", seriesTicker);

        try {
            EventService.EventQuery query = EventService.EventQuery.builder()
                    .seriesTicker(seriesTicker)
                    .withNestedMarkets(false)
                    .build();

            List<Event> events = api.events().getAllEvents(query);

            // Cache in EventManager if available
            if (eventManager != null) {
                eventManager.cacheEvents(events);
            }

            log.debug("Fetched {} events for series: {}", events.size(), seriesTicker);
            return events;

        } catch (Exception e) {
            log.error("Failed to fetch events for series {}: {}", seriesTicker, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the filter's executor service.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== Static Factory Methods ====================

    /**
     * Create a default filter for common use cases.
     *
     * @return Pre-configured EventFilterCriteria for active events in next 7 days
     */
    public static EventFilterCriteria defaultCriteria() {
        return EventFilterCriteria.builder()
                .minStrikeDateFromNow(java.time.Duration.ofHours(1))    // At least 1 hour away
                .maxStrikeDateFromNow(java.time.Duration.ofDays(7))     // Within 7 days
                .build();
    }

    /**
     * Create criteria for near-term events.
     *
     * @return EventFilterCriteria for events in next 24 hours
     */
    public static EventFilterCriteria nearTermCriteria() {
        return EventFilterCriteria.builder()
                .minStrikeDateFromNow(java.time.Duration.ofMinutes(30))  // At least 30 minutes away
                .maxStrikeDateFromNow(java.time.Duration.ofHours(24))    // Within 24 hours
                .build();
    }

    /**
     * Create criteria for weekly events.
     *
     * @return EventFilterCriteria for events in next 7 days
     */
    public static EventFilterCriteria weeklyCriteria() {
        return EventFilterCriteria.builder()
                .minStrikeDateFromNow(java.time.Duration.ofHours(2))
                .maxStrikeDateFromNow(java.time.Duration.ofDays(7))
                .build();
    }

    // ==================== Builder ====================

    /**
     * Builder for EventFilter.
     */
    public static class Builder {
        private final KalshiApi api;
        private EventManager eventManager;
        private int parallelThreads = DEFAULT_PARALLEL_THREADS;

        Builder(KalshiApi api) {
            this.api = api;
        }

        /**
         * Set the EventManager for caching.
         */
        public Builder eventManager(EventManager eventManager) {
            this.eventManager = eventManager;
            return this;
        }

        /**
         * Set number of parallel threads for fetching.
         */
        public Builder parallelThreads(int parallelThreads) {
            this.parallelThreads = parallelThreads;
            return this;
        }

        /**
         * Build the EventFilter.
         */
        public EventFilter build() {
            return new EventFilter(api, eventManager, parallelThreads);
        }
    }

    /**
     * Fluent builder for filtering operations.
     */
    public static class FilterBuilder {
        private final EventFilter filter;
        private final List<String> seriesTickers;
        private EventFilterCriteria.Builder criteriaBuilder = EventFilterCriteria.builder();

        FilterBuilder(EventFilter filter, List<String> seriesTickers) {
            this.filter = filter;
            this.seriesTickers = new ArrayList<>(seriesTickers);
        }

        /**
         * Add more series tickers.
         */
        public FilterBuilder andFrom(String... tickers) {
            seriesTickers.addAll(Arrays.asList(tickers));
            return this;
        }

        /**
         * Set minimum strike date from now.
         */
        public FilterBuilder minStrikeDateFromNow(java.time.Duration duration) {
            criteriaBuilder.minStrikeDateFromNow(duration);
            return this;
        }

        /**
         * Set maximum strike date from now.
         */
        public FilterBuilder maxStrikeDateFromNow(java.time.Duration duration) {
            criteriaBuilder.maxStrikeDateFromNow(duration);
            return this;
        }

        /**
         * Set required category.
         */
        public FilterBuilder category(String category) {
            criteriaBuilder.category(category);
            return this;
        }

        /**
         * Set title contains filter.
         */
        public FilterBuilder titleContains(String text) {
            criteriaBuilder.titleContains(text);
            return this;
        }

        /**
         * Add custom filter.
         */
        public FilterBuilder where(java.util.function.Predicate<Event> predicate) {
            criteriaBuilder.customFilter(predicate);
            return this;
        }

        /**
         * Execute the filter synchronously and return results.
         */
        public EventInterestList execute() {
            return filter.filter(seriesTickers, criteriaBuilder.build());
        }

        /**
         * Execute the filter asynchronously with callbacks.
         *
         * @param onEventMatched Callback for each matching event
         * @param onComplete Callback when all filtering completes
         */
        public void executeAsync(Consumer<Event> onEventMatched, Consumer<EventInterestList> onComplete) {
            filter.filterAsync(seriesTickers, criteriaBuilder.build(), onEventMatched, onComplete);
        }

        /**
         * Execute the filter asynchronously with callbacks.
         *
         * @param onEventMatched Callback for each matching event
         */
        public void executeAsync(Consumer<Event> onEventMatched) {
            filter.filterAsync(seriesTickers, criteriaBuilder.build(), onEventMatched, null);
        }

        /**
         * Execute and return event tickers only (synchronous).
         */
        public List<String> getEventTickers() {
            return execute().getEventTickers();
        }

        /**
         * Execute and return events (synchronous).
         */
        public List<Event> getEvents() {
            return execute().getEvents();
        }
    }
}
