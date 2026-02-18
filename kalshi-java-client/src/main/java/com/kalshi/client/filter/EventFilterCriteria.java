package com.kalshi.client.filter;

import com.kalshi.client.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Configurable criteria for filtering events.
 * Supports various filtering options like strike date range, category, etc.
 * Events without a strike date are automatically filtered out.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * EventFilterCriteria criteria = EventFilterCriteria.builder()
 *     .minStrikeDateFromNow(Duration.ofHours(-8))    // Allow events up to 8 hours in past
 *     .maxStrikeDateFromNow(Duration.ofDays(7))      // No more than 7 days out
 *     .category("Financials")                         // Specific category
 *     .titleContains("S&P")                           // Title filter
 *     .customFilter(e -> e.getMutuallyExclusive())   // Custom predicate
 *     .build();
 * }</pre>
 */
public class EventFilterCriteria {

    private static final Logger log = LoggerFactory.getLogger(EventFilterCriteria.class);

    private final Duration minStrikeDateFromNow;
    private final Duration maxStrikeDateFromNow;
    private final Instant minStrikeDate;
    private final Instant maxStrikeDate;
    private final String category;
    private final List<String> categories;
    private final String titleContains;
    private final String titlePattern;
    private final Boolean mutuallyExclusive;
    private final List<Predicate<Event>> customFilters;
    private boolean loggingEnabled = true;

    private EventFilterCriteria(Builder builder) {
        this.minStrikeDateFromNow = builder.minStrikeDateFromNow;
        this.maxStrikeDateFromNow = builder.maxStrikeDateFromNow;
        this.minStrikeDate = builder.minStrikeDate;
        this.maxStrikeDate = builder.maxStrikeDate;
        this.category = builder.category;
        this.categories = builder.categories;
        this.titleContains = builder.titleContains;
        this.titlePattern = builder.titlePattern;
        this.mutuallyExclusive = builder.mutuallyExclusive;
        this.customFilters = builder.customFilters;
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create default criteria that accepts all events.
     */
    public static EventFilterCriteria acceptAll() {
        return builder().build();
    }

    /**
     * Test if an event matches all configured criteria.
     *
     * @param event Event to test
     * @return true if event matches all criteria
     */
    public boolean matches(Event event) {
        if (event == null) {
            return false;
        }

        String eventId = getEventIdentifier(event);

        // Strike date is required - filter out events without strike date
        Instant strikeDate = event.getStrikeDate();
        if (strikeDate == null) {
            logFilteredOut(eventId, "strikeDate", "strikeDate=null (required: must have strike date)");
            return false;
        }

        Instant now = Instant.now();

        // Check minimum strike date from now (can be negative to allow past events)
        if (minStrikeDateFromNow != null) {
            Instant minDate = now.plus(minStrikeDateFromNow);
            if (strikeDate.isBefore(minDate)) {
                logFilteredOut(eventId, "minStrikeDateFromNow",
                    String.format("strikeDate=%s (required: >= %s, minStrikeDateFromNow=%s)",
                        strikeDate, minDate, minStrikeDateFromNow));
                return false;
            }
        }

        if (maxStrikeDateFromNow != null) {
            Instant maxDate = now.plus(maxStrikeDateFromNow);
            if (strikeDate.isAfter(maxDate)) {
                logFilteredOut(eventId, "maxStrikeDateFromNow",
                    String.format("strikeDate=%s (required: <= %s, maxStrikeDateFromNow=%s)",
                        strikeDate, maxDate, maxStrikeDateFromNow));
                return false;
            }
        }

        if (minStrikeDate != null) {
            if (strikeDate.isBefore(minStrikeDate)) {
                logFilteredOut(eventId, "minStrikeDate",
                    String.format("strikeDate=%s (required: >= %s)", strikeDate, minStrikeDate));
                return false;
            }
        }

        if (maxStrikeDate != null) {
            if (strikeDate.isAfter(maxStrikeDate)) {
                logFilteredOut(eventId, "maxStrikeDate",
                    String.format("strikeDate=%s (required: <= %s)", strikeDate, maxStrikeDate));
                return false;
            }
        }

        // Category check
        if (category != null && !category.isEmpty()) {
            if (event.getCategory() == null || !event.getCategory().equalsIgnoreCase(category)) {
                logFilteredOut(eventId, "category",
                    String.format("category='%s' (required: '%s')",
                        event.getCategory() != null ? event.getCategory() : "null", category));
                return false;
            }
        }

        if (categories != null && !categories.isEmpty()) {
            if (event.getCategory() == null) {
                logFilteredOut(eventId, "categories",
                    String.format("category=null (required: one of %s)", categories));
                return false;
            }
            boolean categoryMatch = false;
            for (String cat : categories) {
                if (event.getCategory().equalsIgnoreCase(cat)) {
                    categoryMatch = true;
                    break;
                }
            }
            if (!categoryMatch) {
                logFilteredOut(eventId, "categories",
                    String.format("category='%s' (required: one of %s)", event.getCategory(), categories));
                return false;
            }
        }

        // Title checks
        if (titleContains != null && !titleContains.isEmpty()) {
            String title = event.getTitle();
            if (title == null || !title.toLowerCase().contains(titleContains.toLowerCase())) {
                logFilteredOut(eventId, "titleContains",
                    String.format("title='%s' (required: contains '%s')",
                        title != null ? truncate(title, 40) : "null", titleContains));
                return false;
            }
        }

        if (titlePattern != null && !titlePattern.isEmpty()) {
            String title = event.getTitle();
            if (title == null || !title.matches(titlePattern)) {
                logFilteredOut(eventId, "titlePattern",
                    String.format("title='%s' (required: matches '%s')",
                        title != null ? truncate(title, 40) : "null", titlePattern));
                return false;
            }
        }

        // Mutually exclusive check
        if (mutuallyExclusive != null) {
            if (event.getMutuallyExclusive() == null || !event.getMutuallyExclusive().equals(mutuallyExclusive)) {
                logFilteredOut(eventId, "mutuallyExclusive",
                    String.format("mutuallyExclusive=%s (required: %s)",
                        event.getMutuallyExclusive(), mutuallyExclusive));
                return false;
            }
        }

        // Custom filters
        if (customFilters != null) {
            int filterIndex = 0;
            for (Predicate<Event> filter : customFilters) {
                if (!filter.test(event)) {
                    logFilteredOut(eventId, "customFilter",
                        String.format("failed custom filter #%d", filterIndex));
                    return false;
                }
                filterIndex++;
            }
        }

        if (loggingEnabled) {
            log.info("EVENT ACCEPTED: {} | category={}, strikeDate={} - passed all filter criteria",
                eventId,
                event.getCategory() != null ? event.getCategory() : "unknown",
                strikeDate);
        }
        return true;
    }

    /**
     * Get a human-readable identifier for the event.
     */
    private String getEventIdentifier(Event event) {
        String ticker = event.getEventTicker();
        String title = event.getTitle();
        if (title != null && !title.isEmpty()) {
            return String.format("%s (%s)", ticker, truncate(title, 40));
        }
        return ticker;
    }

    /**
     * Truncate a string to a maximum length.
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Log the reason an event was filtered out.
     */
    private void logFilteredOut(String eventId, String filterField, String details) {
        if (loggingEnabled) {
            log.info("EVENT FILTERED OUT: {} | field: {} | {}", eventId, filterField, details);
        }
    }

    /**
     * Enable or disable filter logging.
     * @param enabled true to enable logging, false to disable
     * @return this filter for chaining
     */
    public EventFilterCriteria withLogging(boolean enabled) {
        this.loggingEnabled = enabled;
        return this;
    }

    /**
     * Create a Predicate from this criteria.
     */
    public Predicate<Event> toPredicate() {
        return this::matches;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EventFilterCriteria{");
        List<String> parts = new ArrayList<>();

        if (minStrikeDateFromNow != null) parts.add("minStrikeDateFromNow=" + minStrikeDateFromNow);
        if (maxStrikeDateFromNow != null) parts.add("maxStrikeDateFromNow=" + maxStrikeDateFromNow);
        if (minStrikeDate != null) parts.add("minStrikeDate=" + minStrikeDate);
        if (maxStrikeDate != null) parts.add("maxStrikeDate=" + maxStrikeDate);
        if (category != null) parts.add("category=" + category);
        if (categories != null) parts.add("categories=" + categories);
        if (titleContains != null) parts.add("titleContains=" + titleContains);
        if (titlePattern != null) parts.add("titlePattern=" + titlePattern);
        if (mutuallyExclusive != null) parts.add("mutuallyExclusive=" + mutuallyExclusive);
        if (customFilters != null) parts.add("customFilters=" + customFilters.size());

        sb.append(String.join(", ", parts));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Builder for EventFilterCriteria.
     */
    public static class Builder {
        private Duration minStrikeDateFromNow;
        private Duration maxStrikeDateFromNow;
        private Instant minStrikeDate;
        private Instant maxStrikeDate;
        private String category;
        private List<String> categories;
        private String titleContains;
        private String titlePattern;
        private Boolean mutuallyExclusive;
        private List<Predicate<Event>> customFilters;

        /**
         * Minimum time from now until strike date.
         * Events with strike date before (now + duration) are filtered out.
         *
         * <p>Use negative durations to allow past events:</p>
         * <ul>
         *   <li>{@code Duration.ofHours(2)} - strike date must be at least 2 hours in the future</li>
         *   <li>{@code Duration.ofHours(-8)} - allow events with strike date up to 8 hours in the past</li>
         *   <li>{@code Duration.ZERO} - strike date must be now or in the future</li>
         * </ul>
         *
         * @param duration Minimum duration from now (negative for past events)
         */
        public Builder minStrikeDateFromNow(Duration duration) {
            this.minStrikeDateFromNow = duration;
            return this;
        }

        /**
         * Maximum time from now until strike date.
         * Events with strike date after (now + duration) are filtered out.
         *
         * @param duration Maximum duration from now
         */
        public Builder maxStrikeDateFromNow(Duration duration) {
            this.maxStrikeDateFromNow = duration;
            return this;
        }

        /**
         * Minimum absolute strike date.
         */
        public Builder minStrikeDate(Instant minStrikeDate) {
            this.minStrikeDate = minStrikeDate;
            return this;
        }

        /**
         * Maximum absolute strike date.
         */
        public Builder maxStrikeDate(Instant maxStrikeDate) {
            this.maxStrikeDate = maxStrikeDate;
            return this;
        }

        /**
         * Required event category (case-insensitive).
         */
        public Builder category(String category) {
            this.category = category;
            return this;
        }

        /**
         * List of acceptable categories (any match).
         */
        public Builder categories(List<String> categories) {
            this.categories = categories;
            return this;
        }

        /**
         * Required substring in event title (case-insensitive).
         */
        public Builder titleContains(String titleContains) {
            this.titleContains = titleContains;
            return this;
        }

        /**
         * Regex pattern for event title.
         */
        public Builder titlePattern(String titlePattern) {
            this.titlePattern = titlePattern;
            return this;
        }

        /**
         * Require events to be mutually exclusive (or not).
         */
        public Builder mutuallyExclusive(boolean mutuallyExclusive) {
            this.mutuallyExclusive = mutuallyExclusive;
            return this;
        }

        /**
         * Add a custom filter predicate.
         */
        public Builder customFilter(Predicate<Event> filter) {
            if (this.customFilters == null) {
                this.customFilters = new ArrayList<>();
            }
            this.customFilters.add(filter);
            return this;
        }

        /**
         * Build the criteria.
         */
        public EventFilterCriteria build() {
            return new EventFilterCriteria(this);
        }
    }
}
