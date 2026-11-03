package com.tattoostudio.service;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.util.SlidingWindowCounter;

/**
 * Stops anyone from guessing booking codes: after 5 failed code + phone attempts
 * from one IP address within 15 minutes, further attempts are refused until the
 * oldest failure is older than 15 minutes.
 *
 * A successful lookup deliberately does NOT clear the failures: otherwise someone could
 * alternate guesses with lookups of their own booking and never be blocked.
 */
@Component
public class LookupAttemptLimiter {

    private final SlidingWindowCounter failures;
    private final BookingRules rules;

    public LookupAttemptLimiter(BookingRules rules, Clock clock) {
        this.rules = rules;
        this.failures = new SlidingWindowCounter(rules.lookupWindow(), clock);
    }

    public boolean isBlocked(String key) {
        return failures.count(key) >= rules.maxFailedLookups();
    }

    public void recordFailure(String key) {
        failures.record(key);
    }

    @Scheduled(fixedRateString = "${app.booking.lookup-window}")
    public void purgeExpired() {
        failures.purgeExpired();
    }

    boolean isTracking(String key) {
        return failures.isTracking(key);
    }
}
