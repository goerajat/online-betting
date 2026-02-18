package com.kalshi.client.cache;

import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Centralized cache for Series, Events, and Markets data.
 * Provides TTL-based caching to avoid repeated API calls.
 *
 * <p>Thread-safe implementation using ConcurrentHashMap.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * DataCache cache = new DataCache();
 *
 * // Get or fetch series
 * Series series = cache.getSeries("KXINX", () -> api.series().getSeries("KXINX"));
 *
 * // Get or fetch event
 * Event event = cache.getEvent("KXINX-24JAN01", () -> api.events().getEvent("KXINX-24JAN01"));
 *
 * // Get or fetch market
 * Market market = cache.getMarket("KXINX-24JAN01-B5500", () -> api.markets().getMarket("KXINX-24JAN01-B5500"));
 *
 * // Invalidate cache
 * cache.invalidateMarket("KXINX-24JAN01-B5500");
 * cache.invalidateAll();
 * }</pre>
 */
public class DataCache {

    private static final Logger log = LoggerFactory.getLogger(DataCache.class);

    // Default TTL values
    private static final Duration DEFAULT_SERIES_TTL = Duration.ofHours(24);
    private static final Duration DEFAULT_EVENT_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_MARKET_TTL = Duration.ofMinutes(5);

    // Cache entries with expiration
    private final Map<String, CacheEntry<Series>> seriesCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Event>> eventCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Market>> marketCache = new ConcurrentHashMap<>();

    // Events by series ticker (for quick lookup)
    private final Map<String, Set<String>> eventsBySeriesTicker = new ConcurrentHashMap<>();

    // Markets by event ticker (for quick lookup)
    private final Map<String, Set<String>> marketsByEventTicker = new ConcurrentHashMap<>();

    // Configurable TTLs
    private Duration seriesTtl = DEFAULT_SERIES_TTL;
    private Duration eventTtl = DEFAULT_EVENT_TTL;
    private Duration marketTtl = DEFAULT_MARKET_TTL;

    /**
     * Create a new DataCache with default TTL values.
     */
    public DataCache() {
    }

    /**
     * Create a new DataCache with custom TTL values.
     */
    public DataCache(Duration seriesTtl, Duration eventTtl, Duration marketTtl) {
        this.seriesTtl = seriesTtl;
        this.eventTtl = eventTtl;
        this.marketTtl = marketTtl;
    }

    // ==================== Series Cache ====================

    /**
     * Get a series from cache, or fetch it using the supplier if not cached.
     *
     * @param ticker Series ticker
     * @param fetcher Supplier to fetch the series if not cached
     * @return The cached or fetched Series
     */
    public Series getSeries(String ticker, Supplier<Series> fetcher) {
        CacheEntry<Series> entry = seriesCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for series: {}", ticker);
            return entry.getValue();
        }

        log.debug("Cache miss for series: {}, fetching...", ticker);
        Series series = fetcher.get();
        if (series != null) {
            putSeries(series);
        }
        return series;
    }

    /**
     * Get a series from cache only (no fetch).
     *
     * @param ticker Series ticker
     * @return The cached Series or null if not in cache
     */
    public Series getSeriesCached(String ticker) {
        CacheEntry<Series> entry = seriesCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        return null;
    }

    /**
     * Put a series into the cache.
     *
     * @param series The series to cache
     */
    public void putSeries(Series series) {
        if (series != null && series.getTicker() != null) {
            seriesCache.put(series.getTicker(), new CacheEntry<>(series, seriesTtl));
            log.debug("Cached series: {}", series.getTicker());
        }
    }

    /**
     * Put multiple series into the cache.
     *
     * @param seriesList List of series to cache
     */
    public void putAllSeries(List<Series> seriesList) {
        for (Series series : seriesList) {
            putSeries(series);
        }
    }

    /**
     * Invalidate a series from the cache.
     *
     * @param ticker Series ticker to invalidate
     */
    public void invalidateSeries(String ticker) {
        seriesCache.remove(ticker);
        log.debug("Invalidated series cache: {}", ticker);
    }

    // ==================== Event Cache ====================

    /**
     * Get an event from cache, or fetch it using the supplier if not cached.
     *
     * @param ticker Event ticker
     * @param fetcher Supplier to fetch the event if not cached
     * @return The cached or fetched Event
     */
    public Event getEvent(String ticker, Supplier<Event> fetcher) {
        CacheEntry<Event> entry = eventCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for event: {}", ticker);
            return entry.getValue();
        }

        log.debug("Cache miss for event: {}, fetching...", ticker);
        Event event = fetcher.get();
        if (event != null) {
            putEvent(event);
        }
        return event;
    }

    /**
     * Get an event from cache only (no fetch).
     *
     * @param ticker Event ticker
     * @return The cached Event or null if not in cache
     */
    public Event getEventCached(String ticker) {
        CacheEntry<Event> entry = eventCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        return null;
    }

    /**
     * Put an event into the cache.
     *
     * @param event The event to cache
     */
    public void putEvent(Event event) {
        if (event != null && event.getEventTicker() != null) {
            eventCache.put(event.getEventTicker(), new CacheEntry<>(event, eventTtl));

            // Index by series ticker for quick lookup
            if (event.getSeriesTicker() != null) {
                eventsBySeriesTicker
                        .computeIfAbsent(event.getSeriesTicker(), k -> ConcurrentHashMap.newKeySet())
                        .add(event.getEventTicker());
            }

            // Cache nested markets if present
            if (event.getMarkets() != null) {
                for (Market market : event.getMarkets()) {
                    putMarket(market);
                    // Index market by event ticker
                    marketsByEventTicker
                            .computeIfAbsent(event.getEventTicker(), k -> ConcurrentHashMap.newKeySet())
                            .add(market.getTicker());
                }
            }

            log.debug("Cached event: {} with {} markets",
                    event.getEventTicker(),
                    event.getMarkets() != null ? event.getMarkets().size() : 0);
        }
    }

    /**
     * Put multiple events into the cache.
     *
     * @param events List of events to cache
     */
    public void putAllEvents(List<Event> events) {
        for (Event event : events) {
            putEvent(event);
        }
    }

    /**
     * Get all cached events for a series.
     *
     * @param seriesTicker Series ticker
     * @return List of cached events (may be incomplete)
     */
    public List<Event> getEventsBySeriesCached(String seriesTicker) {
        Set<String> eventTickers = eventsBySeriesTicker.get(seriesTicker);
        if (eventTickers == null) {
            return Collections.emptyList();
        }

        List<Event> events = new ArrayList<>();
        for (String ticker : eventTickers) {
            Event event = getEventCached(ticker);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Invalidate an event from the cache.
     *
     * @param ticker Event ticker to invalidate
     */
    public void invalidateEvent(String ticker) {
        CacheEntry<Event> entry = eventCache.remove(ticker);
        if (entry != null && entry.getValue() != null) {
            String seriesTicker = entry.getValue().getSeriesTicker();
            if (seriesTicker != null) {
                Set<String> tickers = eventsBySeriesTicker.get(seriesTicker);
                if (tickers != null) {
                    tickers.remove(ticker);
                }
            }
        }
        // Also remove market index for this event
        marketsByEventTicker.remove(ticker);
        log.debug("Invalidated event cache: {}", ticker);
    }

    // ==================== Market Cache ====================

    /**
     * Get a market from cache, or fetch it using the supplier if not cached.
     *
     * @param ticker Market ticker
     * @param fetcher Supplier to fetch the market if not cached
     * @return The cached or fetched Market
     */
    public Market getMarket(String ticker, Supplier<Market> fetcher) {
        CacheEntry<Market> entry = marketCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for market: {}", ticker);
            return entry.getValue();
        }

        log.debug("Cache miss for market: {}, fetching...", ticker);
        Market market = fetcher.get();
        if (market != null) {
            putMarket(market);
        }
        return market;
    }

    /**
     * Get a market from cache only (no fetch).
     *
     * @param ticker Market ticker
     * @return The cached Market or null if not in cache
     */
    public Market getMarketCached(String ticker) {
        CacheEntry<Market> entry = marketCache.get(ticker);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        return null;
    }

    /**
     * Put a market into the cache.
     *
     * @param market The market to cache
     */
    public void putMarket(Market market) {
        if (market != null && market.getTicker() != null) {
            marketCache.put(market.getTicker(), new CacheEntry<>(market, marketTtl));
            log.debug("Cached market: {}", market.getTicker());
        }
    }

    /**
     * Put multiple markets into the cache.
     *
     * @param markets List of markets to cache
     */
    public void putAllMarkets(List<Market> markets) {
        for (Market market : markets) {
            putMarket(market);
        }
    }

    /**
     * Get all cached markets for an event.
     *
     * @param eventTicker Event ticker
     * @return List of cached markets (may be incomplete)
     */
    public List<Market> getMarketsByEventCached(String eventTicker) {
        Set<String> marketTickers = marketsByEventTicker.get(eventTicker);
        if (marketTickers == null) {
            return Collections.emptyList();
        }

        List<Market> markets = new ArrayList<>();
        for (String ticker : marketTickers) {
            Market market = getMarketCached(ticker);
            if (market != null) {
                markets.add(market);
            }
        }
        return markets;
    }

    /**
     * Invalidate a market from the cache.
     *
     * @param ticker Market ticker to invalidate
     */
    public void invalidateMarket(String ticker) {
        marketCache.remove(ticker);
        log.debug("Invalidated market cache: {}", ticker);
    }

    // ==================== Bulk Operations ====================

    /**
     * Invalidate all caches.
     */
    public void invalidateAll() {
        seriesCache.clear();
        eventCache.clear();
        marketCache.clear();
        eventsBySeriesTicker.clear();
        marketsByEventTicker.clear();
        log.info("Invalidated all caches");
    }

    /**
     * Remove expired entries from all caches.
     */
    public void cleanupExpired() {
        int seriesRemoved = cleanupExpiredEntries(seriesCache);
        int eventsRemoved = cleanupExpiredEntries(eventCache);
        int marketsRemoved = cleanupExpiredEntries(marketCache);

        if (seriesRemoved + eventsRemoved + marketsRemoved > 0) {
            log.info("Cache cleanup: removed {} series, {} events, {} markets",
                    seriesRemoved, eventsRemoved, marketsRemoved);
        }
    }

    private <T> int cleanupExpiredEntries(Map<String, CacheEntry<T>> cache) {
        int removed = 0;
        Iterator<Map.Entry<String, CacheEntry<T>>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isExpired()) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    // ==================== Statistics ====================

    /**
     * Get cache statistics.
     *
     * @return CacheStats object with current cache state
     */
    public CacheStats getStats() {
        return new CacheStats(
                seriesCache.size(),
                eventCache.size(),
                marketCache.size()
        );
    }

    // ==================== Configuration ====================

    /**
     * Set the TTL for series cache entries.
     */
    public void setSeriesTtl(Duration ttl) {
        this.seriesTtl = ttl;
    }

    /**
     * Set the TTL for event cache entries.
     */
    public void setEventTtl(Duration ttl) {
        this.eventTtl = ttl;
    }

    /**
     * Set the TTL for market cache entries.
     */
    public void setMarketTtl(Duration ttl) {
        this.marketTtl = ttl;
    }

    // ==================== Inner Classes ====================

    /**
     * Cache entry with expiration time.
     */
    private static class CacheEntry<T> {
        private final T value;
        private final Instant expiresAt;

        CacheEntry(T value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        T getValue() {
            return value;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Cache statistics.
     */
    public static class CacheStats {
        private final int seriesCount;
        private final int eventCount;
        private final int marketCount;

        CacheStats(int seriesCount, int eventCount, int marketCount) {
            this.seriesCount = seriesCount;
            this.eventCount = eventCount;
            this.marketCount = marketCount;
        }

        public int getSeriesCount() {
            return seriesCount;
        }

        public int getEventCount() {
            return eventCount;
        }

        public int getMarketCount() {
            return marketCount;
        }

        public int getTotalCount() {
            return seriesCount + eventCount + marketCount;
        }

        @Override
        public String toString() {
            return "CacheStats{series=" + seriesCount +
                    ", events=" + eventCount +
                    ", markets=" + marketCount + "}";
        }
    }
}
