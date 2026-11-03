package com.tattoostudio.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.model.Booking;

/**
 * Privacy clean-up: 24 hours after an appointment ends, the customer's name, phone,
 * email and notes are removed. The booking itself (service, artist, time, price, status) is kept,
 * so the admin statistics still work without holding anyone's personal data.
 */
@Component
public class AnonymizationJob {

    private static final Logger log = LoggerFactory.getLogger(AnonymizationJob.class);

    private final MongoTemplate mongo;
    private final BookingRules rules;
    private final Clock clock;

    public AnonymizationJob(MongoTemplate mongo, BookingRules rules, Clock clock) {
        this.mongo = mongo;
        this.rules = rules;
        this.clock = clock;
    }

    /** Runs once when the server has started (catches up after downtime), then at the start of every hour. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        long wiped = anonymizeEndedBefore(clock.instant().minus(rules.anonymizeAfter()));
        if (wiped > 0) {
            log.info("Anonymised customer details of {} past booking(s)", wiped);
        }
    }

    /** Wipes customer details of every booking that ended before {@code cutoff}; returns how many. */
    public long anonymizeEndedBefore(Instant cutoff) {
        Query query = Query.query(Criteria.where("anonymized").is(false).and("endsAt").lt(cutoff));
        Update update = new Update()
                .unset("customerName")
                .unset("phone")
                .unset("email")
                .unset("notes")
                .set("anonymized", true);
        return mongo.updateMulti(query, update, Booking.class).getModifiedCount();
    }
}
