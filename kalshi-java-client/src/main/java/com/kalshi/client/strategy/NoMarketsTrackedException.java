package com.kalshi.client.strategy;

/**
 * Exception thrown when an EventStrategy has no markets to track after filtering.
 * This typically occurs when:
 * <ul>
 *   <li>All markets are filtered out by volume requirements</li>
 *   <li>All markets are filtered out by expiration requirements</li>
 *   <li>All markets have inactive status</li>
 *   <li>The event has no markets at all</li>
 * </ul>
 *
 * <p>Strategies can configure whether to throw this exception or continue
 * without tracked markets using {@link EventStrategy#setFailOnNoMarkets(boolean)}.</p>
 *
 * @see EventStrategy
 */
public class NoMarketsTrackedException extends RuntimeException {

    private final String eventTicker;
    private final int totalMarkets;
    private final String filterDescription;

    /**
     * Create a new NoMarketsTrackedException.
     *
     * @param eventTicker       The event ticker that was being initialized
     * @param totalMarkets      Total number of markets in the event
     * @param filterDescription Description of the filter that was applied
     */
    public NoMarketsTrackedException(String eventTicker, int totalMarkets, String filterDescription) {
        super(buildMessage(eventTicker, totalMarkets, filterDescription));
        this.eventTicker = eventTicker;
        this.totalMarkets = totalMarkets;
        this.filterDescription = filterDescription;
    }

    /**
     * Create a new NoMarketsTrackedException with a cause.
     *
     * @param eventTicker       The event ticker that was being initialized
     * @param totalMarkets      Total number of markets in the event
     * @param filterDescription Description of the filter that was applied
     * @param cause             The underlying cause
     */
    public NoMarketsTrackedException(String eventTicker, int totalMarkets, String filterDescription, Throwable cause) {
        super(buildMessage(eventTicker, totalMarkets, filterDescription), cause);
        this.eventTicker = eventTicker;
        this.totalMarkets = totalMarkets;
        this.filterDescription = filterDescription;
    }

    private static String buildMessage(String eventTicker, int totalMarkets, String filterDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("No markets to track for event '").append(eventTicker).append("'");

        if (totalMarkets == 0) {
            sb.append(" - event has no markets");
        } else {
            sb.append(" - all ").append(totalMarkets).append(" markets were filtered out");
        }

        if (filterDescription != null && !filterDescription.isEmpty()) {
            sb.append(" by filter: ").append(filterDescription);
        }

        return sb.toString();
    }

    /**
     * Get the event ticker that failed initialization.
     */
    public String getEventTicker() {
        return eventTicker;
    }

    /**
     * Get the total number of markets in the event (before filtering).
     */
    public int getTotalMarkets() {
        return totalMarkets;
    }

    /**
     * Get the description of the filter that was applied.
     */
    public String getFilterDescription() {
        return filterDescription;
    }
}
