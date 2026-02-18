package com.kalshi.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.kalshi.client.KalshiClient;
import com.kalshi.client.exception.KalshiApiException;
import com.kalshi.client.model.PaginatedResponse;
import com.kalshi.client.model.Series;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for retrieving series data from Kalshi API.
 * Supports cursor-based pagination for list endpoints.
 * Logs timing statistics for API calls and parsing.
 */
public class SeriesService {

    private static final Logger log = LoggerFactory.getLogger(SeriesService.class);
    private static final int DEFAULT_LIMIT = 100;

    private final KalshiClient client;

    // Cumulative timing statistics
    private long totalApiTimeMs = 0;
    private long totalParseTimeMs = 0;
    private int totalApiCalls = 0;

    public SeriesService(KalshiClient client) {
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
     * Get a specific series by ticker.
     *
     * @param seriesTicker The series ticker
     * @return The Series object
     */
    public Series getSeries(String seriesTicker) {
        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get("/series/" + seriesTicker, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        Series series = parseSeriesResponse(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        log.info("Series {} loaded: apiTime={}ms, parseTime={}ms",
                seriesTicker, apiTimeMs, parseTimeMs);

        return series;
    }

    private Series parseSeriesResponse(String json) {
        try {
            JsonNode root = client.getObjectMapper().readTree(json);
            JsonNode seriesNode = root.get("series");
            if (seriesNode == null) {
                throw new KalshiApiException("Series not found in response");
            }
            return client.getObjectMapper().treeToValue(seriesNode, Series.class);
        } catch (KalshiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse series response", e);
        }
    }

    /**
     * Get a list of series (first page).
     *
     * @return List of Series objects (first page only)
     */
    public List<Series> getSeriesList() {
        return getSeriesList(new SeriesQuery());
    }

    /**
     * Get a list of series with custom query parameters (first page).
     *
     * @param query Series query parameters
     * @return List of Series objects
     */
    public List<Series> getSeriesList(SeriesQuery query) {
        return getSeriesListPaginated(query).getData();
    }

    /**
     * Get series with pagination info.
     *
     * @param query Series query parameters
     * @return PaginatedResponse containing series and cursor
     */
    public PaginatedResponse<Series> getSeriesListPaginated(SeriesQuery query) {
        String path = "/series" + query.toQueryString();

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        PaginatedResponse<Series> result = parseSeriesPaginated(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        log.info("Series page loaded: apiTime={}ms, parseTime={}ms, series={}",
                apiTimeMs, parseTimeMs, result.size());

        return result;
    }

    /**
     * Get all series by automatically paginating through all pages.
     *
     * @return List of all Series objects
     */
    public List<Series> getAllSeries() {
        return getAllSeries(new SeriesQuery());
    }

    /**
     * Get all series matching the query by automatically paginating.
     *
     * @param baseQuery Base query parameters (cursor will be overwritten)
     * @return List of all Series objects
     */
    public List<Series> getAllSeries(SeriesQuery baseQuery) {
        List<Series> allSeries = new ArrayList<>();
        String cursor = null;
        int pageNumber = 0;

        // Track timing for this batch
        long batchApiTime = 0;
        long batchParseTime = 0;
        long batchStartTime = System.currentTimeMillis();

        log.info("Fetching all series (auto-pagination)...");

        do {
            pageNumber++;
            SeriesQuery query = SeriesQuery.builder()
                    .limit(baseQuery.limit != null ? baseQuery.limit : DEFAULT_LIMIT)
                    .cursor(cursor)
                    .status(baseQuery.status)
                    .build();

            // Track timing before/after for this page
            long pageApiTimeBefore = totalApiTimeMs;
            long pageParseTimeBefore = totalParseTimeMs;

            PaginatedResponse<Series> page = getSeriesListPaginated(query);

            // Calculate this page's timing
            long pageApiTime = totalApiTimeMs - pageApiTimeBefore;
            long pageParseTime = totalParseTimeMs - pageParseTimeBefore;
            batchApiTime += pageApiTime;
            batchParseTime += pageParseTime;

            allSeries.addAll(page.getData());
            cursor = page.getCursor();

            log.info("Series page {} complete: {} items, total so far: {}, hasMore: {}",
                    pageNumber, page.size(), allSeries.size(), page.hasMore());
        } while (cursor != null && !cursor.isEmpty());

        long batchTotalTime = System.currentTimeMillis() - batchStartTime;

        log.info("=== Series Loading Summary ===");
        log.info("  Total series: {}", allSeries.size());
        log.info("  API calls: {}, Total time: {}ms", pageNumber, batchTotalTime);
        log.info("  Kalshi API time: {}ms ({}%)", batchApiTime,
                batchTotalTime > 0 ? (batchApiTime * 100 / batchTotalTime) : 0);
        log.info("  Parse/create time: {}ms ({}%)", batchParseTime,
                batchTotalTime > 0 ? (batchParseTime * 100 / batchTotalTime) : 0);
        log.info("  Overhead time: {}ms ({}%)", batchTotalTime - batchApiTime - batchParseTime,
                batchTotalTime > 0 ? ((batchTotalTime - batchApiTime - batchParseTime) * 100 / batchTotalTime) : 0);

        return allSeries;
    }

    /**
     * Get all series for a specific category.
     *
     * @param category Category name
     * @return List of Series objects
     */
    public List<Series> getSeriesByCategory(String category) {
        List<Series> allSeries = getAllSeries();
        List<Series> filtered = new ArrayList<>();
        for (Series series : allSeries) {
            if (category.equalsIgnoreCase(series.getCategory())) {
                filtered.add(series);
            }
        }
        return filtered;
    }

    private PaginatedResponse<Series> parseSeriesPaginated(String json) {
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

            // Extract series
            JsonNode seriesNode = root.get("series");
            List<Series> series;
            if (seriesNode == null || !seriesNode.isArray()) {
                series = new ArrayList<>();
            } else {
                series = client.getObjectMapper().convertValue(seriesNode,
                        new TypeReference<List<Series>>() {});
            }

            return new PaginatedResponse<>(series, cursor);
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse series response", e);
        }
    }

    /**
     * Query builder for series endpoint.
     */
    public static class SeriesQuery {
        private Integer limit;
        private String cursor;
        private String status;

        public static Builder builder() {
            return new Builder();
        }

        public String toQueryString() {
            List<String> params = new ArrayList<>();

            if (limit != null) params.add("limit=" + limit);
            if (cursor != null) params.add("cursor=" + cursor);
            if (status != null) params.add("status=" + status);

            return params.isEmpty() ? "" : "?" + String.join("&", params);
        }

        public static class Builder {
            private final SeriesQuery query = new SeriesQuery();

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

            public SeriesQuery build() {
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
