package com.kalshi.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.kalshi.client.KalshiClient;
import com.kalshi.client.exception.KalshiApiException;
import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.PaginatedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for retrieving event data from Kalshi API.
 * Supports cursor-based pagination for list endpoints.
 * Logs timing statistics for API calls and parsing.
 */
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final int DEFAULT_LIMIT = 100;

    private final KalshiClient client;

    // Cumulative timing statistics
    private long totalApiTimeMs = 0;
    private long totalParseTimeMs = 0;
    private int totalApiCalls = 0;

    public EventService(KalshiClient client) {
        this.client = client;
    }

    /**
     * Get cumulative timing statistics.
     */
    public TimingStats getTimingStats() {
        return new TimingStats(totalApiTimeMs, totalParseTimeMs, totalApiCalls);
    }

    /**
     * Reset timing statistics.
     */
    public void resetTimingStats() {
        totalApiTimeMs = 0;
        totalParseTimeMs = 0;
        totalApiCalls = 0;
    }

    /**
     * Get a specific event by ticker.
     * Note: Nested markets are never fetched. Use MarketService to get markets.
     *
     * @param eventTicker The event ticker
     * @return The Event object (without nested markets)
     */
    public Event getEvent(String eventTicker) {
        String path = "/events/" + eventTicker;

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        Event event = parseEventResponse(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        int marketCount = event.getMarkets() != null ? event.getMarkets().size() : 0;
        log.info("Event {} loaded: apiTime={}ms, parseTime={}ms, markets={}",
                eventTicker, apiTimeMs, parseTimeMs, marketCount);

        return event;
    }

    /**
     * Get events with default pagination (first page, 100 results).
     *
     * @return List of Event objects (first page only)
     */
    public List<Event> getEvents() {
        return getEvents(new EventQuery());
    }

    /**
     * Get events with custom query parameters.
     *
     * @param query Event query parameters
     * @return List of Event objects (single page)
     */
    public List<Event> getEvents(EventQuery query) {
        return getEventsPaginated(query).getData();
    }

    /**
     * Get events with pagination info.
     *
     * @param query Event query parameters
     * @return PaginatedResponse containing events and cursor
     */
    public PaginatedResponse<Event> getEventsPaginated(EventQuery query) {
        String path = "/events" + query.toQueryString();

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        PaginatedResponse<Event> result = parseEventsPaginated(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        // Count total markets across all events
        int totalMarkets = 0;
        for (Event event : result.getData()) {
            if (event.getMarkets() != null) {
                totalMarkets += event.getMarkets().size();
            }
        }

        log.info("Events page loaded: apiTime={}ms, parseTime={}ms, events={}, markets={}",
                apiTimeMs, parseTimeMs, result.size(), totalMarkets);

        return result;
    }

    /**
     * Get all events by automatically paginating through all pages.
     *
     * @return List of all Event objects
     */
    public List<Event> getAllEvents() {
        return getAllEvents(new EventQuery());
    }

    /**
     * Get all events matching the query by automatically paginating.
     *
     * @param baseQuery Base query parameters (cursor will be overwritten)
     * @return List of all Event objects
     */
    public List<Event> getAllEvents(EventQuery baseQuery) {
        List<Event> allEvents = new ArrayList<>();
        String cursor = null;
        int pageNumber = 0;

        // Reset stats for this batch operation
        long batchApiTime = 0;
        long batchParseTime = 0;
        long batchStartTime = System.currentTimeMillis();

        log.info("Fetching all events (auto-pagination){}...",
                baseQuery.seriesTicker != null ? " for series " + baseQuery.seriesTicker : "");

        do {
            pageNumber++;
            EventQuery query = EventQuery.builder()
                    .limit(baseQuery.limit != null ? baseQuery.limit : DEFAULT_LIMIT)
                    .cursor(cursor)
                    .status(baseQuery.status)
                    .seriesTicker(baseQuery.seriesTicker)
                    .withNestedMarkets(baseQuery.withNestedMarkets)
                    .build();

            // Track timing before/after for this page
            long pageApiTimeBefore = totalApiTimeMs;
            long pageParseTimeBefore = totalParseTimeMs;

            PaginatedResponse<Event> page = getEventsPaginated(query);

            // Calculate this page's timing
            long pageApiTime = totalApiTimeMs - pageApiTimeBefore;
            long pageParseTime = totalParseTimeMs - pageParseTimeBefore;
            batchApiTime += pageApiTime;
            batchParseTime += pageParseTime;

            allEvents.addAll(page.getData());
            cursor = page.getCursor();

            log.info("Events page {} complete: {} items, total so far: {}, hasMore: {}",
                    pageNumber, page.size(), allEvents.size(), page.hasMore());
        } while (cursor != null && !cursor.isEmpty());

        long batchTotalTime = System.currentTimeMillis() - batchStartTime;

        // Count total markets
        int totalMarkets = 0;
        for (Event event : allEvents) {
            if (event.getMarkets() != null) {
                totalMarkets += event.getMarkets().size();
            }
        }

        log.info("=== Event Loading Summary ===");
        log.info("  Total events: {}, Total markets: {}", allEvents.size(), totalMarkets);
        log.info("  API calls: {}, Total time: {}ms", pageNumber, batchTotalTime);
        log.info("  Kalshi API time: {}ms ({}%)", batchApiTime,
                batchTotalTime > 0 ? (batchApiTime * 100 / batchTotalTime) : 0);
        log.info("  Parse/create time: {}ms ({}%)", batchParseTime,
                batchTotalTime > 0 ? (batchParseTime * 100 / batchTotalTime) : 0);
        log.info("  Overhead time: {}ms ({}%)", batchTotalTime - batchApiTime - batchParseTime,
                batchTotalTime > 0 ? ((batchTotalTime - batchApiTime - batchParseTime) * 100 / batchTotalTime) : 0);

        return allEvents;
    }

    /**
     * Get events for a specific series (first page).
     *
     * @param seriesTicker Series ticker
     * @return List of Event objects
     */
    public List<Event> getEventsBySeries(String seriesTicker) {
        return getEvents(EventQuery.builder().seriesTicker(seriesTicker).build());
    }

    /**
     * Get all events for a specific series (auto-pagination).
     *
     * @param seriesTicker Series ticker
     * @return List of all Event objects for the series
     */
    public List<Event> getAllEventsBySeries(String seriesTicker) {
        return getAllEvents(EventQuery.builder().seriesTicker(seriesTicker).build());
    }

    private Event parseEventResponse(String json) {
        try {
            JsonNode root = client.getObjectMapper().readTree(json);
            JsonNode eventNode = root.get("event");
            if (eventNode == null) {
                throw new KalshiApiException("Event not found in response");
            }

            Event event = client.getObjectMapper().treeToValue(eventNode, Event.class);

            // Parse nested markets if present
            JsonNode marketsNode = root.get("markets");
            if (marketsNode != null && marketsNode.isArray()) {
                List<Market> markets = client.getObjectMapper().convertValue(marketsNode,
                    new TypeReference<List<Market>>() {});
                event.setMarkets(markets);
            }

            return event;
        } catch (KalshiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse event response", e);
        }
    }

    private PaginatedResponse<Event> parseEventsPaginated(String json) {
        try {
            JsonNode root = client.getObjectMapper().readTree(json);

            // Extract cursor
            String cursor = null;
            JsonNode cursorNode = root.get("cursor");
            if (cursorNode != null && !cursorNode.isNull()) {
                cursor = cursorNode.asText();
                if (cursor.isEmpty()) {
                    cursor = null;
                }
            }

            // Extract events
            JsonNode eventsNode = root.get("events");
            List<Event> events;
            if (eventsNode == null || !eventsNode.isArray()) {
                events = new ArrayList<>();
            } else {
                events = client.getObjectMapper().convertValue(eventsNode,
                        new TypeReference<List<Event>>() {});
            }

            return new PaginatedResponse<>(events, cursor);
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse events response", e);
        }
    }

    /**
     * Query builder for events endpoint.
     */
    public static class EventQuery {
        private Integer limit;
        private String cursor;
        private String status;
        private String seriesTicker;
        private Boolean withNestedMarkets;

        public static Builder builder() {
            return new Builder();
        }

        public String toQueryString() {
            List<String> params = new ArrayList<>();

            if (limit != null) params.add("limit=" + limit);
            if (cursor != null) params.add("cursor=" + cursor);
            if (status != null) params.add("status=" + status);
            if (seriesTicker != null) params.add("series_ticker=" + seriesTicker);
            if (withNestedMarkets != null) params.add("with_nested_markets=" + withNestedMarkets);

            return params.isEmpty() ? "" : "?" + String.join("&", params);
        }

        public static class Builder {
            private final EventQuery query = new EventQuery();

            public Builder limit(int limit) {
                query.limit = limit;
                return this;
            }

            public Builder cursor(String cursor) {
                query.cursor = cursor;
                return this;
            }

            public Builder status(String status) {
                query.status = status;
                return this;
            }

            public Builder seriesTicker(String seriesTicker) {
                query.seriesTicker = seriesTicker;
                return this;
            }

            public Builder withNestedMarkets(Boolean withNestedMarkets) {
                query.withNestedMarkets = withNestedMarkets;
                return this;
            }

            public EventQuery build() {
                return query;
            }
        }
    }

    /**
     * Timing statistics for API calls and parsing.
     */
    public static class TimingStats {
        private final long totalApiTimeMs;
        private final long totalParseTimeMs;
        private final int totalApiCalls;

        public TimingStats(long totalApiTimeMs, long totalParseTimeMs, int totalApiCalls) {
            this.totalApiTimeMs = totalApiTimeMs;
            this.totalParseTimeMs = totalParseTimeMs;
            this.totalApiCalls = totalApiCalls;
        }

        public long getTotalApiTimeMs() {
            return totalApiTimeMs;
        }

        public long getTotalParseTimeMs() {
            return totalParseTimeMs;
        }

        public int getTotalApiCalls() {
            return totalApiCalls;
        }

        public long getTotalTimeMs() {
            return totalApiTimeMs + totalParseTimeMs;
        }

        public double getApiTimePercent() {
            long total = getTotalTimeMs();
            return total > 0 ? (totalApiTimeMs * 100.0 / total) : 0;
        }

        public double getParseTimePercent() {
            long total = getTotalTimeMs();
            return total > 0 ? (totalParseTimeMs * 100.0 / total) : 0;
        }

        @Override
        public String toString() {
            return String.format("TimingStats{apiCalls=%d, apiTime=%dms (%.1f%%), parseTime=%dms (%.1f%%)}",
                    totalApiCalls, totalApiTimeMs, getApiTimePercent(),
                    totalParseTimeMs, getParseTimePercent());
        }
    }
}
