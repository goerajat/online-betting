package com.kalshi.client.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.kalshi.client.KalshiClient;
import com.kalshi.client.exception.KalshiApiException;
import com.kalshi.client.model.Market;
import com.kalshi.client.model.Orderbook;
import com.kalshi.client.model.PaginatedResponse;
import com.kalshi.client.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for retrieving market data from Kalshi API.
 * Supports cursor-based pagination for list endpoints.
 * Logs timing statistics for API calls and parsing.
 */
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final KalshiClient client;

    // Cumulative timing statistics
    private long totalApiTimeMs = 0;
    private long totalParseTimeMs = 0;
    private int totalApiCalls = 0;

    public MarketService(KalshiClient client) {
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
     * Get a specific market by ticker.
     *
     * @param ticker Market ticker
     * @return Market object
     */
    public Market getMarket(String ticker) {
        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get("/markets/" + ticker, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        Market market = parseMarketResponse(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        log.info("Market {} loaded: apiTime={}ms, parseTime={}ms",
                ticker, apiTimeMs, parseTimeMs);

        return market;
    }

    private Market parseMarketResponse(String json) {
        try {
            JsonNode root = client.getObjectMapper().readTree(json);
            JsonNode marketNode = root.get("market");
            if (marketNode == null) {
                throw new KalshiApiException("Market not found in response");
            }
            return client.getObjectMapper().treeToValue(marketNode, Market.class);
        } catch (KalshiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse market response", e);
        }
    }

    /**
     * Get markets with default pagination (first page, 100 results).
     *
     * @return List of Market objects (first page only)
     */
    public List<Market> getMarkets() {
        return getMarkets(new MarketQuery());
    }

    /**
     * Get markets with custom query parameters.
     *
     * @param query Market query parameters
     * @return List of Market objects (single page)
     */
    public List<Market> getMarkets(MarketQuery query) {
        return getMarketsPaginated(query).getData();
    }

    /**
     * Get markets with pagination info.
     *
     * @param query Market query parameters
     * @return PaginatedResponse containing markets and cursor
     */
    public PaginatedResponse<Market> getMarketsPaginated(MarketQuery query) {
        String path = "/markets" + query.toQueryString();

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        PaginatedResponse<Market> result = parseMarketsPaginated(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        log.info("Markets page loaded: apiTime={}ms, parseTime={}ms, markets={}",
                apiTimeMs, parseTimeMs, result.size());

        return result;
    }

    /**
     * Get all markets by automatically paginating through all pages.
     *
     * @return List of all Market objects
     */
    public List<Market> getAllMarkets() {
        return getAllMarkets(new MarketQuery());
    }

    /**
     * Get all markets matching the query by automatically paginating.
     *
     * @param baseQuery Base query parameters (cursor will be overwritten)
     * @return List of all Market objects
     */
    public List<Market> getAllMarkets(MarketQuery baseQuery) {
        List<Market> allMarkets = new ArrayList<>();
        String cursor = null;
        int pageNumber = 0;

        // Track timing for this batch
        long batchApiTime = 0;
        long batchParseTime = 0;
        long batchStartTime = System.currentTimeMillis();

        log.info("Fetching all markets (auto-pagination){}...",
                baseQuery.eventTicker != null ? " for event " + baseQuery.eventTicker :
                baseQuery.seriesTicker != null ? " for series " + baseQuery.seriesTicker : "");

        do {
            pageNumber++;
            MarketQuery query = MarketQuery.builder()
                    .limit(baseQuery.limit != null ? baseQuery.limit : DEFAULT_LIMIT)
                    .cursor(cursor)
                    .eventTicker(baseQuery.eventTicker)
                    .seriesTicker(baseQuery.seriesTicker)
                    .tickers(baseQuery.tickers)
                    .status(baseQuery.status)
                    .build();

            // Track timing before/after for this page
            long pageApiTimeBefore = totalApiTimeMs;
            long pageParseTimeBefore = totalParseTimeMs;

            PaginatedResponse<Market> page = getMarketsPaginated(query);

            // Calculate this page's timing
            long pageApiTime = totalApiTimeMs - pageApiTimeBefore;
            long pageParseTime = totalParseTimeMs - pageParseTimeBefore;
            batchApiTime += pageApiTime;
            batchParseTime += pageParseTime;

            allMarkets.addAll(page.getData());
            cursor = page.getCursor();

            log.info("Markets page {} complete: {} items, total so far: {}, hasMore: {}",
                    pageNumber, page.size(), allMarkets.size(), page.hasMore());
        } while (cursor != null && !cursor.isEmpty());

        long batchTotalTime = System.currentTimeMillis() - batchStartTime;

        log.info("=== Market Loading Summary ===");
        log.info("  Total markets: {}", allMarkets.size());
        log.info("  API calls: {}, Total time: {}ms", pageNumber, batchTotalTime);
        log.info("  Kalshi API time: {}ms ({}%)", batchApiTime,
                batchTotalTime > 0 ? (batchApiTime * 100 / batchTotalTime) : 0);
        log.info("  Parse/create time: {}ms ({}%)", batchParseTime,
                batchTotalTime > 0 ? (batchParseTime * 100 / batchTotalTime) : 0);
        log.info("  Overhead time: {}ms ({}%)", batchTotalTime - batchApiTime - batchParseTime,
                batchTotalTime > 0 ? ((batchTotalTime - batchApiTime - batchParseTime) * 100 / batchTotalTime) : 0);

        return allMarkets;
    }

    /**
     * Get markets for a specific event.
     *
     * @param eventTicker Event ticker
     * @return List of Market objects
     */
    public List<Market> getMarketsByEvent(String eventTicker) {
        return getMarkets(MarketQuery.builder().eventTicker(eventTicker).build());
    }

    /**
     * Get all markets for a specific event (auto-pagination).
     *
     * @param eventTicker Event ticker
     * @return List of all Market objects for the event
     */
    public List<Market> getAllMarketsByEvent(String eventTicker) {
        return getAllMarkets(MarketQuery.builder().eventTicker(eventTicker).build());
    }

    /**
     * Get markets for a specific series.
     *
     * @param seriesTicker Series ticker
     * @return List of Market objects
     */
    public List<Market> getMarketsBySeries(String seriesTicker) {
        return getMarkets(MarketQuery.builder().seriesTicker(seriesTicker).build());
    }

    /**
     * Get all markets for a specific series (auto-pagination).
     *
     * @param seriesTicker Series ticker
     * @return List of all Market objects for the series
     */
    public List<Market> getAllMarketsBySeries(String seriesTicker) {
        return getAllMarkets(MarketQuery.builder().seriesTicker(seriesTicker).build());
    }

    /**
     * Get the orderbook for a specific market.
     *
     * @param ticker Market ticker
     * @return Orderbook object
     */
    public Orderbook getOrderbook(String ticker) {
        return getOrderbook(ticker, null);
    }

    /**
     * Get the orderbook for a specific market with depth limit.
     *
     * @param ticker Market ticker
     * @param depth Maximum depth levels to return
     * @return Orderbook object
     */
    public Orderbook getOrderbook(String ticker, Integer depth) {
        String path = "/markets/" + ticker + "/orderbook";
        if (depth != null) {
            path += "?depth=" + depth;
        }

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path, String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        Orderbook orderbook = parseOrderbookResponse(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        int yesLevels = orderbook.getYes() != null ? orderbook.getYes().size() : 0;
        int noLevels = orderbook.getNo() != null ? orderbook.getNo().size() : 0;
        log.debug("Orderbook {} loaded: apiTime={}ms, parseTime={}ms, yesLevels={}, noLevels={}",
                ticker, apiTimeMs, parseTimeMs, yesLevels, noLevels);

        return orderbook;
    }

    private Orderbook parseOrderbookResponse(String json) {
        try {
            JsonNode root = client.getObjectMapper().readTree(json);
            JsonNode orderbookNode = root.get("orderbook");
            if (orderbookNode == null) {
                throw new KalshiApiException("Orderbook not found in response");
            }
            return client.getObjectMapper().treeToValue(orderbookNode, Orderbook.class);
        } catch (KalshiApiException e) {
            throw e;
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse orderbook response", e);
        }
    }

    /**
     * Get recent trades for a specific market (first page).
     *
     * @param ticker Market ticker
     * @return List of Trade objects
     */
    public List<Trade> getTrades(String ticker) {
        return getTrades(ticker, null, null);
    }

    /**
     * Get trades for a specific market with pagination parameters.
     *
     * @param ticker Market ticker
     * @param limit Maximum number of trades to return
     * @param cursor Pagination cursor
     * @return List of Trade objects
     */
    public List<Trade> getTrades(String ticker, Integer limit, String cursor) {
        return getTradesPaginated(ticker, limit, cursor).getData();
    }

    /**
     * Get trades with pagination info.
     *
     * @param ticker Market ticker
     * @param limit Maximum number of trades to return
     * @param cursor Pagination cursor
     * @return PaginatedResponse containing trades and cursor
     */
    public PaginatedResponse<Trade> getTradesPaginated(String ticker, Integer limit, String cursor) {
        StringBuilder path = new StringBuilder("/markets/" + ticker + "/trades");
        List<String> params = new ArrayList<>();

        if (limit != null) params.add("limit=" + limit);
        if (cursor != null) params.add("cursor=" + cursor);

        if (!params.isEmpty()) {
            path.append("?").append(String.join("&", params));
        }

        // Time the API call
        long apiStart = System.currentTimeMillis();
        String response = client.get(path.toString(), String.class);
        long apiEnd = System.currentTimeMillis();
        long apiTimeMs = apiEnd - apiStart;

        // Time the parsing
        long parseStart = System.currentTimeMillis();
        PaginatedResponse<Trade> result = parseTradesPaginated(response);
        long parseEnd = System.currentTimeMillis();
        long parseTimeMs = parseEnd - parseStart;

        // Update cumulative stats
        totalApiTimeMs += apiTimeMs;
        totalParseTimeMs += parseTimeMs;
        totalApiCalls++;

        log.info("Trades page loaded for {}: apiTime={}ms, parseTime={}ms, trades={}",
                ticker, apiTimeMs, parseTimeMs, result.size());

        return result;
    }

    /**
     * Get all trades for a market by automatically paginating.
     *
     * @param ticker Market ticker
     * @return List of all Trade objects
     */
    public List<Trade> getAllTrades(String ticker) {
        return getAllTrades(ticker, DEFAULT_LIMIT);
    }

    /**
     * Get all trades for a market by automatically paginating.
     *
     * @param ticker Market ticker
     * @param pageSize Number of trades per page
     * @return List of all Trade objects
     */
    public List<Trade> getAllTrades(String ticker, int pageSize) {
        List<Trade> allTrades = new ArrayList<>();
        String cursor = null;
        int pageNumber = 0;

        // Track timing for this batch
        long batchApiTime = 0;
        long batchParseTime = 0;
        long batchStartTime = System.currentTimeMillis();

        log.info("Fetching all trades for {} (auto-pagination)...", ticker);

        do {
            pageNumber++;

            // Track timing before/after for this page
            long pageApiTimeBefore = totalApiTimeMs;
            long pageParseTimeBefore = totalParseTimeMs;

            PaginatedResponse<Trade> page = getTradesPaginated(ticker, pageSize, cursor);

            // Calculate this page's timing
            long pageApiTime = totalApiTimeMs - pageApiTimeBefore;
            long pageParseTime = totalParseTimeMs - pageParseTimeBefore;
            batchApiTime += pageApiTime;
            batchParseTime += pageParseTime;

            allTrades.addAll(page.getData());
            cursor = page.getCursor();

            log.info("Trades page {} complete for {}: {} items, total so far: {}, hasMore: {}",
                    pageNumber, ticker, page.size(), allTrades.size(), page.hasMore());
        } while (cursor != null && !cursor.isEmpty());

        long batchTotalTime = System.currentTimeMillis() - batchStartTime;

        log.info("=== Trades Loading Summary for {} ===", ticker);
        log.info("  Total trades: {}", allTrades.size());
        log.info("  API calls: {}, Total time: {}ms", pageNumber, batchTotalTime);
        log.info("  Kalshi API time: {}ms ({}%)", batchApiTime,
                batchTotalTime > 0 ? (batchApiTime * 100 / batchTotalTime) : 0);
        log.info("  Parse/create time: {}ms ({}%)", batchParseTime,
                batchTotalTime > 0 ? (batchParseTime * 100 / batchTotalTime) : 0);
        log.info("  Overhead time: {}ms ({}%)", batchTotalTime - batchApiTime - batchParseTime,
                batchTotalTime > 0 ? ((batchTotalTime - batchApiTime - batchParseTime) * 100 / batchTotalTime) : 0);

        return allTrades;
    }

    private PaginatedResponse<Market> parseMarketsPaginated(String json) {
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

            // Extract markets
            JsonNode marketsNode = root.get("markets");
            List<Market> markets;
            if (marketsNode == null || !marketsNode.isArray()) {
                markets = new ArrayList<>();
            } else {
                markets = client.getObjectMapper().convertValue(marketsNode,
                        new TypeReference<List<Market>>() {});
            }

            return new PaginatedResponse<>(markets, cursor);
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse markets response", e);
        }
    }

    private PaginatedResponse<Trade> parseTradesPaginated(String json) {
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

            // Extract trades
            JsonNode tradesNode = root.get("trades");
            List<Trade> trades;
            if (tradesNode == null || !tradesNode.isArray()) {
                trades = new ArrayList<>();
            } else {
                trades = client.getObjectMapper().convertValue(tradesNode,
                        new TypeReference<List<Trade>>() {});
            }

            return new PaginatedResponse<>(trades, cursor);
        } catch (Exception e) {
            throw new KalshiApiException("Failed to parse trades response", e);
        }
    }

    /**
     * Query builder for markets endpoint.
     */
    public static class MarketQuery {
        private Integer limit;
        private String cursor;
        private String eventTicker;
        private String seriesTicker;
        private String tickers;
        private String status;
        private String mveFilter;

        public static Builder builder() {
            return new Builder();
        }

        public String toQueryString() {
            List<String> params = new ArrayList<>();

            if (limit != null) params.add("limit=" + limit);
            if (cursor != null) params.add("cursor=" + cursor);
            if (eventTicker != null) params.add("event_ticker=" + eventTicker);
            if (seriesTicker != null) params.add("series_ticker=" + seriesTicker);
            if (tickers != null) params.add("tickers=" + tickers);
            if (status != null) params.add("status=" + status);
            if (mveFilter != null) params.add("mve_filter=" + mveFilter);

            return params.isEmpty() ? "" : "?" + String.join("&", params);
        }

        public static class Builder {
            private final MarketQuery query = new MarketQuery();

            public Builder limit(int limit) {
                query.limit = Math.min(limit, MAX_LIMIT);
                return this;
            }

            public Builder cursor(String cursor) {
                query.cursor = cursor;
                return this;
            }

            public Builder eventTicker(String eventTicker) {
                query.eventTicker = eventTicker;
                return this;
            }

            public Builder seriesTicker(String seriesTicker) {
                query.seriesTicker = seriesTicker;
                return this;
            }

            public Builder tickers(String tickers) {
                query.tickers = tickers;
                return this;
            }

            public Builder tickers(String... tickers) {
                query.tickers = String.join(",", tickers);
                return this;
            }

            public Builder status(String status) {
                query.status = status;
                return this;
            }

            public Builder mveFilter(String mveFilter) {
                query.mveFilter = mveFilter;
                return this;
            }

            public MarketQuery build() {
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
