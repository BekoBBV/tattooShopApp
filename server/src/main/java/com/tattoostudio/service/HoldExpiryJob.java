package com.tattoostudio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute, releases flash bookings whose 15 minutes to pay the deposit ran out, so their time
 * and design can be booked again. (Availability already ignores lapsed holds; this tidies them up.)
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingService bookings;

    public HoldExpiryJob(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT10S")
    public void run() {
        long released = bookings.expireHolds();
        if (released > 0) {
            log.info("Released {} flash booking(s) whose deposit wasn't paid in time", released);
        }
    }
}
