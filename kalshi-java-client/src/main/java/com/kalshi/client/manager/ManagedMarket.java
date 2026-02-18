package com.kalshi.client.manager;

import com.kalshi.client.model.Market;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Represents a market with live orderbook state.
 * Combines static market information from REST API with real-time orderbook updates from WebSocket.
 *
 * <p>The orderbook is maintained as sorted maps for efficient best bid/ask lookups
 * and supports incremental delta updates.</p>
 */
public class ManagedMarket {

    private final String ticker;
    private volatile Market market;
    private volatile Instant lastMarketUpdate;
    private volatile Instant lastOrderbookUpdate;

    // Orderbook state: price -> quantity (sorted descending for bids)
    private final NavigableMap<Integer, Integer> yesBids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    private final NavigableMap<Integer, Integer> noBids = new ConcurrentSkipListMap<>(Collections.reverseOrder());

    public ManagedMarket(String ticker) {
        this.ticker = Objects.requireNonNull(ticker);
    }

    public ManagedMarket(String ticker, Market market) {
        this.ticker = Objects.requireNonNull(ticker);
        this.market = market;
        this.lastMarketUpdate = Instant.now();
    }

    // ==================== Market Info ====================

    public String getTicker() {
        return ticker;
    }

    public Market getMarket() {
        return market;
    }

    public void setMarket(Market market) {
        this.market = market;
        this.lastMarketUpdate = Instant.now();
    }

    public Instant getLastMarketUpdate() {
        return lastMarketUpdate;
    }

    public Instant getLastOrderbookUpdate() {
        return lastOrderbookUpdate;
    }

    public String getTitle() {
        return market != null ? market.getTitle() : ticker;
    }

    public String getStatus() {
        return market != null ? market.getStatus() : null;
    }

    public boolean isActive() {
        return "active".equalsIgnoreCase(getStatus());
    }

    // ==================== Orderbook Updates ====================

    /**
     * Apply an orderbook snapshot (replaces all existing data).
     *
     * @param yesLevels List of [price, quantity] pairs for yes bids
     * @param noLevels List of [price, quantity] pairs for no bids
     */
    public void applySnapshot(List<List<Number>> yesLevels, List<List<Number>> noLevels) {
        yesBids.clear();
        noBids.clear();

        if (yesLevels != null) {
            for (List<Number> level : yesLevels) {
                int price = level.get(0).intValue();
                int qty = level.get(1).intValue();
                if (qty > 0) {
                    yesBids.put(price, qty);
                }
            }
        }

        if (noLevels != null) {
            for (List<Number> level : noLevels) {
                int price = level.get(0).intValue();
                int qty = level.get(1).intValue();
                if (qty > 0) {
                    noBids.put(price, qty);
                }
            }
        }

        lastOrderbookUpdate = Instant.now();
    }

    /**
     * Apply a delta update to the orderbook.
     *
     * @param isYesSide True for yes side, false for no side
     * @param price Price level
     * @param delta Quantity change (positive = add, negative = remove)
     */
    public void applyDelta(boolean isYesSide, int price, int delta) {
        NavigableMap<Integer, Integer> bids = isYesSide ? yesBids : noBids;

        int currentQty = bids.getOrDefault(price, 0);
        int newQty = currentQty + delta;

        if (newQty <= 0) {
            bids.remove(price);
        } else {
            bids.put(price, newQty);
        }

        lastOrderbookUpdate = Instant.now();
    }

    /**
     * Clear the orderbook.
     */
    public void clearOrderbook() {
        yesBids.clear();
        noBids.clear();
        lastOrderbookUpdate = null;
    }

    // ==================== Orderbook Queries ====================

    /**
     * Get all yes bid levels (sorted by price descending).
     */
    public List<PriceLevel> getYesBids() {
        return toLevelList(yesBids);
    }

    /**
     * Get all no bid levels (sorted by price descending).
     */
    public List<PriceLevel> getNoBids() {
        return toLevelList(noBids);
    }

    /**
     * Get yes bid levels limited to top N.
     */
    public List<PriceLevel> getYesBids(int limit) {
        return toLevelList(yesBids, limit);
    }

    /**
     * Get no bid levels limited to top N.
     */
    public List<PriceLevel> getNoBids(int limit) {
        return toLevelList(noBids, limit);
    }

    /**
     * Get yes ask levels (derived from no bids: ask = 100 - bid).
     */
    public List<PriceLevel> getYesAsks() {
        List<PriceLevel> asks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : noBids.entrySet()) {
            asks.add(new PriceLevel(100 - entry.getKey(), entry.getValue()));
        }
        asks.sort(Comparator.comparingInt(PriceLevel::getPrice));
        return asks;
    }

    /**
     * Get no ask levels (derived from yes bids: ask = 100 - bid).
     */
    public List<PriceLevel> getNoAsks() {
        List<PriceLevel> asks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : yesBids.entrySet()) {
            asks.add(new PriceLevel(100 - entry.getKey(), entry.getValue()));
        }
        asks.sort(Comparator.comparingInt(PriceLevel::getPrice));
        return asks;
    }

    /**
     * Get best yes bid price.
     */
    public Integer getBestYesBid() {
        return yesBids.isEmpty() ? null : yesBids.firstKey();
    }

    /**
     * Get best no bid price.
     */
    public Integer getBestNoBid() {
        return noBids.isEmpty() ? null : noBids.firstKey();
    }

    /**
     * Get best yes ask price (derived from best no bid).
     */
    public Integer getBestYesAsk() {
        Integer bestNoBid = getBestNoBid();
        return bestNoBid != null ? 100 - bestNoBid : null;
    }

    /**
     * Get best no ask price (derived from best yes bid).
     */
    public Integer getBestNoAsk() {
        Integer bestYesBid = getBestYesBid();
        return bestYesBid != null ? 100 - bestYesBid : null;
    }

    /**
     * Get yes bid-ask spread.
     */
    public Integer getYesSpread() {
        Integer bid = getBestYesBid();
        Integer ask = getBestYesAsk();
        if (bid == null || ask == null) return null;
        return ask - bid;
    }

    /**
     * Get no bid-ask spread.
     */
    public Integer getNoSpread() {
        Integer bid = getBestNoBid();
        Integer ask = getBestNoAsk();
        if (bid == null || ask == null) return null;
        return ask - bid;
    }

    /**
     * Get total yes bid depth.
     */
    public long getTotalYesBidDepth() {
        return yesBids.values().stream().mapToLong(Integer::longValue).sum();
    }

    /**
     * Get total no bid depth.
     */
    public long getTotalNoBidDepth() {
        return noBids.values().stream().mapToLong(Integer::longValue).sum();
    }

    /**
     * Get number of yes bid price levels.
     */
    public int getYesBidLevelCount() {
        return yesBids.size();
    }

    /**
     * Get number of no bid price levels.
     */
    public int getNoBidLevelCount() {
        return noBids.size();
    }

    /**
     * Check if orderbook has been initialized.
     */
    public boolean hasOrderbook() {
        return lastOrderbookUpdate != null;
    }

    private List<PriceLevel> toLevelList(NavigableMap<Integer, Integer> map) {
        List<PriceLevel> levels = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            levels.add(new PriceLevel(entry.getKey(), entry.getValue()));
        }
        return levels;
    }

    private List<PriceLevel> toLevelList(NavigableMap<Integer, Integer> map, int limit) {
        List<PriceLevel> levels = new ArrayList<>();
        int count = 0;
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            if (count++ >= limit) break;
            levels.add(new PriceLevel(entry.getKey(), entry.getValue()));
        }
        return levels;
    }

    @Override
    public String toString() {
        return "ManagedMarket{" +
                "ticker='" + ticker + '\'' +
                ", title='" + getTitle() + '\'' +
                ", status='" + getStatus() + '\'' +
                ", bestYesBid=" + getBestYesBid() +
                ", bestYesAsk=" + getBestYesAsk() +
                ", yesBidLevels=" + yesBids.size() +
                ", noBidLevels=" + noBids.size() +
                '}';
    }

    // ==================== Price Level Class ====================

    /**
     * Represents a price level in the orderbook.
     */
    public static class PriceLevel {
        private final int price;
        private final int quantity;

        public PriceLevel(int price, int quantity) {
            this.price = price;
            this.quantity = quantity;
        }

        public int getPrice() {
            return price;
        }

        public int getQuantity() {
            return quantity;
        }

        @Override
        public String toString() {
            return price + "¢ x " + quantity;
        }
    }
}
