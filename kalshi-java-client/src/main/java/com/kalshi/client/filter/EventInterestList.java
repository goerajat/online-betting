package com.kalshi.client.filter;

import com.kalshi.client.model.Event;
import com.kalshi.client.model.Market;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Container for filtered events of interest.
 * Provides convenient access methods and statistics about the filtered events.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * EventInterestList interestList = eventFilter.filter(seriesTickers, criteria);
 *
 * // Access events
 * for (Event event : interestList.getEvents()) {
 *     System.out.println(event.getTitle());
 * }
 *
 * // Get event tickers for strategy creation
 * List<String> tickers = interestList.getEventTickers();
 *
 * // Statistics
 * System.out.println("Total events: " + interestList.size());
 * System.out.println("Total markets: " + interestList.getTotalMarketCount());
 *
 * // Query by various criteria
 * List<Event> upcoming = interestList.getEventsByStrikeDateRange(
 *     Instant.now(), Instant.now().plus(Duration.ofDays(1)));
 * }</pre>
 */
public class EventInterestList {

    private final List<Event> events;
    private final Map<String, Event> eventsByTicker;
    private final Map<String, List<Event>> eventsBySeries;
    private final Instant createdAt;

    /**
     * Create an EventInterestList from a list of events.
     *
     * @param events List of filtered events
     */
    public EventInterestList(List<Event> events) {
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.eventsByTicker = events.stream()
                .filter(e -> e.getEventTicker() != null)
                .collect(Collectors.toMap(Event::getEventTicker, Function.identity(), (a, b) -> a));
        this.eventsBySeries = events.stream()
                .filter(e -> e.getSeriesTicker() != null)
                .collect(Collectors.groupingBy(Event::getSeriesTicker));
        this.createdAt = Instant.now();
    }

    /**
     * Create an empty EventInterestList.
     */
    public static EventInterestList empty() {
        return new EventInterestList(Collections.emptyList());
    }

    // ==================== Basic Access ====================

    /**
     * Get all events in the interest list.
     *
     * @return Unmodifiable list of events
     */
    public List<Event> getEvents() {
        return events;
    }

    /**
     * Get all event tickers.
     *
     * @return List of event ticker strings
     */
    public List<String> getEventTickers() {
        return events.stream()
                .map(Event::getEventTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get an event by its ticker.
     *
     * @param eventTicker Event ticker
     * @return Event or null if not found
     */
    public Event getEvent(String eventTicker) {
        return eventsByTicker.get(eventTicker);
    }

    /**
     * Check if the list contains a specific event.
     *
     * @param eventTicker Event ticker
     * @return true if the event is in the list
     */
    public boolean contains(String eventTicker) {
        return eventsByTicker.containsKey(eventTicker);
    }

    /**
     * Get the number of events.
     */
    public int size() {
        return events.size();
    }

    /**
     * Check if the list is empty.
     */
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * Get when this interest list was created.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    // ==================== Series-based Access ====================

    /**
     * Get all unique series tickers in the interest list.
     *
     * @return Set of series tickers
     */
    public Set<String> getSeriesTickers() {
        return Collections.unmodifiableSet(eventsBySeries.keySet());
    }

    /**
     * Get events for a specific series.
     *
     * @param seriesTicker Series ticker
     * @return List of events for the series (empty if none)
     */
    public List<Event> getEventsBySeries(String seriesTicker) {
        return eventsBySeries.getOrDefault(seriesTicker, Collections.emptyList());
    }

    /**
     * Get number of events per series.
     *
     * @return Map of series ticker to event count
     */
    public Map<String, Integer> getEventCountBySeries() {
        return eventsBySeries.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));
    }

    // ==================== Market Statistics ====================

    /**
     * Get total market count across all events.
     */
    public int getTotalMarketCount() {
        return events.stream()
                .mapToInt(e -> e.getMarkets() != null ? e.getMarkets().size() : 0)
                .sum();
    }

    /**
     * Get all markets from all events.
     *
     * @return List of all markets
     */
    public List<Market> getAllMarkets() {
        return events.stream()
                .filter(e -> e.getMarkets() != null)
                .flatMap(e -> e.getMarkets().stream())
                .collect(Collectors.toList());
    }

    /**
     * Get all market tickers from all events.
     *
     * @return List of market ticker strings
     */
    public List<String> getAllMarketTickers() {
        return getAllMarkets().stream()
                .map(Market::getTicker)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get average market count per event.
     */
    public double getAverageMarketCount() {
        if (events.isEmpty()) return 0;
        return (double) getTotalMarketCount() / events.size();
    }

    // ==================== Date-based Queries ====================

    /**
     * Get events sorted by strike date (ascending).
     *
     * @return List of events sorted by strike date
     */
    public List<Event> getEventsSortedByStrikeDate() {
        return events.stream()
                .sorted(Comparator.comparing(
                        Event::getStrikeDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Get events within a strike date range.
     *
     * @param from Start of range (inclusive)
     * @param to End of range (inclusive)
     * @return List of events within range
     */
    public List<Event> getEventsByStrikeDateRange(Instant from, Instant to) {
        return events.stream()
                .filter(e -> e.getStrikeDate() != null)
                .filter(e -> !e.getStrikeDate().isBefore(from))
                .filter(e -> !e.getStrikeDate().isAfter(to))
                .collect(Collectors.toList());
    }

    /**
     * Get the earliest strike date among all events.
     */
    public Optional<Instant> getEarliestStrikeDate() {
        return events.stream()
                .map(Event::getStrikeDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    /**
     * Get the latest strike date among all events.
     */
    public Optional<Instant> getLatestStrikeDate() {
        return events.stream()
                .map(Event::getStrikeDate)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
    }

    // ==================== Category-based Queries ====================

    /**
     * Get all unique categories in the interest list.
     *
     * @return Set of category strings
     */
    public Set<String> getCategories() {
        return events.stream()
                .map(Event::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get events for a specific category.
     *
     * @param category Category name
     * @return List of events in the category
     */
    public List<Event> getEventsByCategory(String category) {
        return events.stream()
                .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * Get number of events per category.
     *
     * @return Map of category to event count
     */
    public Map<String, Long> getEventCountByCategory() {
        return events.stream()
                .filter(e -> e.getCategory() != null)
                .collect(Collectors.groupingBy(Event::getCategory, Collectors.counting()));
    }

    // ==================== Filtering & Transformation ====================

    /**
     * Create a new interest list filtered by additional criteria.
     *
     * @param criteria Additional criteria to apply
     * @return New EventInterestList with filtered events
     */
    public EventInterestList filter(EventFilterCriteria criteria) {
        List<Event> filtered = events.stream()
                .filter(criteria::matches)
                .collect(Collectors.toList());
        return new EventInterestList(filtered);
    }

    /**
     * Limit the list to the first N events (by strike date).
     *
     * @param maxEvents Maximum number of events
     * @return New EventInterestList limited to maxEvents
     */
    public EventInterestList limit(int maxEvents) {
        List<Event> limited = getEventsSortedByStrikeDate().stream()
                .limit(maxEvents)
                .collect(Collectors.toList());
        return new EventInterestList(limited);
    }

    /**
     * Get a subset of the interest list by event tickers.
     *
     * @param eventTickers Event tickers to include
     * @return New EventInterestList with only the specified events
     */
    public EventInterestList subset(Collection<String> eventTickers) {
        Set<String> tickerSet = new HashSet<>(eventTickers);
        List<Event> subset = events.stream()
                .filter(e -> tickerSet.contains(e.getEventTicker()))
                .collect(Collectors.toList());
        return new EventInterestList(subset);
    }

    // ==================== Summary ====================

    /**
     * Get a summary of the interest list.
     *
     * @return Summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("EventInterestList Summary:\n");
        sb.append("  Events: ").append(events.size()).append("\n");
        sb.append("  Total Markets: ").append(getTotalMarketCount()).append("\n");
        sb.append("  Series: ").append(eventsBySeries.size()).append("\n");
        sb.append("  Categories: ").append(getCategories()).append("\n");

        getEarliestStrikeDate().ifPresent(d ->
                sb.append("  Earliest Strike: ").append(d).append("\n"));
        getLatestStrikeDate().ifPresent(d ->
                sb.append("  Latest Strike: ").append(d).append("\n"));

        sb.append("  Created At: ").append(createdAt);
        return sb.toString();
    }

    @Override
    public String toString() {
        return "EventInterestList{" +
                "events=" + events.size() +
                ", series=" + eventsBySeries.size() +
                ", totalMarkets=" + getTotalMarketCount() +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * Create an iterator over the events.
     */
    public Iterator<Event> iterator() {
        return events.iterator();
    }
}
