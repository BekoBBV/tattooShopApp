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
import com.tattoostudio.storage.ImageUploads;

/**
 * Privacy clean-up: some months after an appointment ends (app.booking.anonymize-after, 180 days),
 * the customer's name, phone, email, date of birth, notes, idea and reference photos are removed.
 * Long enough for an artist to plan the next sessions of a large piece; the booking itself (type,
 * artist, time, price, status, deposit) is kept, so the admin statistics still work without holding
 * anyone's personal data.
 */
@Component
public class AnonymizationJob {

    private static final Logger log = LoggerFactory.getLogger(AnonymizationJob.class);

    private final MongoTemplate mongo;
    private final ImageUploads images;
    private final BookingRules rules;
    private final Clock clock;

    public AnonymizationJob(MongoTemplate mongo, ImageUploads images, BookingRules rules, Clock clock) {
        this.mongo = mongo;
        this.images = images;
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
        long wiped = 0;
        for (Booking booking : mongo.find(query, Booking.class)) {
            Update update = new Update()
                    .unset("customerName")
                    .unset("phone")
                    .unset("email")
                    .unset("dateOfBirth")
                    .unset("notes")
                    .unset("idea")
                    .set("photos", java.util.List.of())
                    .set("anonymized", true);
            boolean changed = mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.getId()).and("anonymized").is(false)),
                    update, Booking.class).getModifiedCount() == 1;
            if (changed) {
                images.deleteAll(booking.getPhotos());
                wiped++;
            }
        }
        return wiped;
    }
}
