package com.kalshi.client.strategy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single log entry in the strategy activity log.
 */
public class LogEntry {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /**
     * Log level for activity entries.
     */
    public enum Level {
        INFO,
        WARN,
        ERROR,
        TRADE  // Special level for order/fill events
    }

    private final Instant timestamp;
    private final Level level;
    private final String message;

    public LogEntry(Level level, String message) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = message;
    }

    public LogEntry(Instant timestamp, Level level, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Get formatted timestamp string.
     */
    public String getFormattedTime() {
        return TIME_FORMATTER.format(timestamp);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", getFormattedTime(), level, message);
    }
}
