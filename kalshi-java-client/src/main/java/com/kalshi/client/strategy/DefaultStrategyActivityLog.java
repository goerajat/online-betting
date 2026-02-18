package com.kalshi.client.strategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Default implementation of StrategyActivityLog with a circular buffer.
 * Thread-safe for concurrent access.
 */
public class DefaultStrategyActivityLog implements StrategyActivityLog {

    private static final int DEFAULT_MAX_ENTRIES = 100;

    private final int maxEntries;
    private final LinkedList<LogEntry> entries;
    private final List<Consumer<LogEntry>> listeners;
    private final Object lock = new Object();

    /**
     * Create a new activity log with default capacity (100 entries).
     */
    public DefaultStrategyActivityLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Create a new activity log with specified capacity.
     *
     * @param maxEntries Maximum number of entries to retain
     */
    public DefaultStrategyActivityLog(int maxEntries) {
        this.maxEntries = maxEntries;
        this.entries = new LinkedList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    @Override
    public void info(String message) {
        addEntry(new LogEntry(LogEntry.Level.INFO, message));
    }

    @Override
    public void warn(String message) {
        addEntry(new LogEntry(LogEntry.Level.WARN, message));
    }

    @Override
    public void error(String message) {
        addEntry(new LogEntry(LogEntry.Level.ERROR, message));
    }

    @Override
    public void error(String message, Throwable error) {
        String fullMessage = message + ": " + error.getMessage();
        addEntry(new LogEntry(LogEntry.Level.ERROR, fullMessage));
    }

    @Override
    public void trade(String message) {
        addEntry(new LogEntry(LogEntry.Level.TRADE, message));
    }

    /**
     * Add an entry to the log.
     */
    private void addEntry(LogEntry entry) {
        synchronized (lock) {
            entries.addFirst(entry);
            while (entries.size() > maxEntries) {
                entries.removeLast();
            }
        }

        // Notify listeners outside the lock
        for (Consumer<LogEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                // Ignore listener errors
            }
        }
    }

    @Override
    public List<LogEntry> getRecentEntries(int count) {
        synchronized (lock) {
            int actualCount = Math.min(count, entries.size());
            return new ArrayList<>(entries.subList(0, actualCount));
        }
    }

    @Override
    public List<LogEntry> getAllEntries() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    @Override
    public int getEntryCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    @Override
    public void addListener(Consumer<LogEntry> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    @Override
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
    }

    /**
     * Get the maximum number of entries this log can hold.
     */
    public int getMaxEntries() {
        return maxEntries;
    }
}
