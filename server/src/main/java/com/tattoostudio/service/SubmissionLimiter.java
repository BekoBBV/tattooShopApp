package com.tattoostudio.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.util.SlidingWindowCounter;

/**
 * Spam protection for the public forms: one IP address may post at most
 * app.limits.reviews-per-hour reviews and app.limits.messages-per-hour contact messages per hour,
 * and make at most app.limits.bookings-per-hour bookings, so a script can't fill the calendar
 * with made-up phone numbers.
 */
@Component
public class SubmissionLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final SlidingWindowCounter reviews;
    private final SlidingWindowCounter messages;
    private final SlidingWindowCounter bookings;
    private final int maxReviews;
    private final int maxMessages;
    private final int maxBookings;

    public SubmissionLimiter(Clock clock,
            @Value("${app.limits.reviews-per-hour}") int maxReviews,
            @Value("${app.limits.messages-per-hour}") int maxMessages,
            @Value("${app.limits.bookings-per-hour:10}") int maxBookings) {
        this.reviews = new SlidingWindowCounter(WINDOW, clock);
        this.messages = new SlidingWindowCounter(WINDOW, clock);
        this.bookings = new SlidingWindowCounter(WINDOW, clock);
        this.maxReviews = maxReviews;
        this.maxMessages = maxMessages;
        this.maxBookings = maxBookings;
    }

    /** @throws ApiException 429 when this IP already sent too many reviews this hour */
    public void checkReview(String ip) {
        if (!reviews.tryRecord(ip, maxReviews)) {
            throw tooMany("reviews");
        }
    }

    /** @throws ApiException 429 when this IP already sent too many messages this hour */
    public void checkMessage(String ip) {
        if (!messages.tryRecord(ip, maxMessages)) {
            throw tooMany("messages");
        }
    }

    /**
     * @throws ApiException 429 when this IP already made too many bookings this hour. Only bookings
     *         that went through count ({@link #recordBooking}), so a taken time doesn't use up the allowance.
     */
    public void checkBooking(String ip) {
        if (bookings.count(ip) >= maxBookings) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_SUBMISSIONS,
                    "Several bookings were made from this device already. Please try again in an hour or call the shop.");
        }
    }

    public void recordBooking(String ip) {
        bookings.record(ip);
    }

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void purgeExpired() {
        reviews.purgeExpired();
        messages.purgeExpired();
        bookings.purgeExpired();
    }

    private static ApiException tooMany(String what) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_SUBMISSIONS,
                "You've sent several " + what + " already. Please try again in an hour.");
    }
}
