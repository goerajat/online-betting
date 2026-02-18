package com.kalshi.client.model;

import java.util.List;
import java.util.Objects;

/**
 * Wrapper for paginated API responses.
 * Contains the data items and a cursor for fetching the next page.
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * // Fetch first page
 * PaginatedResponse<Market> page1 = marketService.getMarketsPaginated(
 *     MarketQuery.builder().limit(100).build());
 *
 * // Fetch next page using cursor
 * if (page1.hasMore()) {
 *     PaginatedResponse<Market> page2 = marketService.getMarketsPaginated(
 *         MarketQuery.builder().limit(100).cursor(page1.getCursor()).build());
 * }
 *
 * // Or use automatic pagination
 * List<Market> allMarkets = marketService.getAllMarkets();
 * }</pre>
 *
 * @param <T> The type of items in the response
 */
public class PaginatedResponse<T> {

    private final List<T> data;
    private final String cursor;

    /**
     * Create a paginated response.
     *
     * @param data List of items
     * @param cursor Cursor for the next page, or null if no more pages
     */
    public PaginatedResponse(List<T> data, String cursor) {
        this.data = Objects.requireNonNull(data, "data must not be null");
        this.cursor = cursor;
    }

    /**
     * Get the data items.
     *
     * @return List of items (never null)
     */
    public List<T> getData() {
        return data;
    }

    /**
     * Get the cursor for the next page.
     *
     * @return Cursor string, or null if no more pages
     */
    public String getCursor() {
        return cursor;
    }

    /**
     * Check if there are more pages available.
     *
     * @return true if cursor is not null/empty
     */
    public boolean hasMore() {
        return cursor != null && !cursor.isEmpty();
    }

    /**
     * Get the number of items in this page.
     *
     * @return Number of items
     */
    public int size() {
        return data.size();
    }

    /**
     * Check if this page is empty.
     *
     * @return true if no items
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public String toString() {
        return "PaginatedResponse{" +
                "dataSize=" + data.size() +
                ", hasMore=" + hasMore() +
                '}';
    }
}
