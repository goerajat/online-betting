package com.kalshi.client.strategy;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for strategy-specific activity logging.
 * Allows strategies to log messages that can be displayed in a UI.
 */
public interface StrategyActivityLog {

    /**
     * Log an informational message.
     *
     * @param message The message to log
     */
    void info(String message);

    /**
     * Log a warning message.
     *
     * @param message The message to log
     */
    void warn(String message);

    /**
     * Log an error message.
     *
     * @param message The message to log
     */
    void error(String message);

    /**
     * Log an error message with exception details.
     *
     * @param message The message to log
     * @param error   The exception
     */
    void error(String message, Throwable error);

    /**
     * Log a trade-related message (order/fill events).
     *
     * @param message The message to log
     */
    void trade(String message);

    /**
     * Get recent log entries.
     *
     * @param count Maximum number of entries to return
     * @return List of recent entries, newest first
     */
    List<LogEntry> getRecentEntries(int count);

    /**
     * Get all log entries.
     *
     * @return List of all entries, newest first
     */
    List<LogEntry> getAllEntries();

    /**
     * Get the total number of entries.
     *
     * @return Entry count
     */
    int getEntryCount();

    /**
     * Add a listener to be notified of new log entries.
     *
     * @param listener The listener to add
     */
    void addListener(Consumer<LogEntry> listener);

    /**
     * Remove a listener.
     *
     * @param listener The listener to remove
     */
    void removeListener(Consumer<LogEntry> listener);

    /**
     * Clear all log entries.
     */
    void clear();
}
