package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.tattoostudio.error.ApiException;
import com.tattoostudio.support.MutableClock;

/** One visitor (IP) can make only so many bookings per hour; only bookings that went through count. */
class SubmissionLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2030-01-07T06:00:00Z"));
    private final SubmissionLimiter limiter = new SubmissionLimiter(clock, 3, 5, 2);

    @Test
    void aVisitorCanMakeOnlySoManyBookingsPerHour() {
        limiter.checkBooking("10.0.0.1");
        limiter.recordBooking("10.0.0.1");
        limiter.checkBooking("10.0.0.1");
        limiter.recordBooking("10.0.0.1");

        assertThatThrownBy(() -> limiter.checkBooking("10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getCode().name()).isEqualTo("TOO_MANY_SUBMISSIONS"));
        assertThatCode(() -> limiter.checkBooking("10.0.0.2")).doesNotThrowAnyException(); // another visitor

        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> limiter.checkBooking("10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void failedAttemptsDoNotUseUpTheAllowance() {
        for (int i = 0; i < 10; i++) {
            limiter.checkBooking("10.0.0.3"); // e.g. the time was taken: never recorded
        }
        assertThatCode(() -> limiter.checkBooking("10.0.0.3")).doesNotThrowAnyException();
    }
}
