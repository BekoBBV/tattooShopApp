package com.tattoostudio.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts events per key (e.g. per IP address) within a sliding time window.
 * Kept in memory, which is enough for a single server.
 */
public class SlidingWindowCounter {

    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();
    private final Duration window;
    private final Clock clock;

    public SlidingWindowCounter(Duration window, Clock clock) {
        this.window = window;
        this.clock = clock;
    }

    /** How many events this key had within the window. */
    public int count(String key) {
        Deque<Instant> recent = events.get(key);
        if (recent == null) {
            return 0;
        }
        synchronized (recent) {
            dropExpired(recent);
            return recent.size();
        }
    }

    public void record(String key) {
        Deque<Instant> recent = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent);
            recent.addLast(clock.instant());
        }
    }

    /** Records the event only if the key is still under {@code max}; returns whether it was allowed. */
    public boolean tryRecord(String key, int max) {
        Deque<Instant> recent = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent);
            if (recent.size() >= max) {
                return false;
            }
            recent.addLast(clock.instant());
            return true;
        }
    }

    /** Forgets keys whose events have all expired, so memory does not keep growing. */
    public void purgeExpired() {
        events.entrySet().removeIf(entry -> {
            Deque<Instant> recent = entry.getValue();
            synchronized (recent) {
                dropExpired(recent);
                return recent.isEmpty();
            }
        });
    }

    public boolean isTracking(String key) {
        return events.containsKey(key);
    }

    private void dropExpired(Deque<Instant> recent) {
        Instant cutoff = clock.instant().minus(window);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
            recent.removeFirst();
        }
    }
}
