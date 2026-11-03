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
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.repository.ArtistRepository;

/**
 * Emails customers a reminder {@code app.booking.reminder-before} (24 h) before their appointment,
 * with the booking code and the cancel link, so fewer chairs stay empty.
 *
 * Only booked appointments with an email get one, and only once ({@code reminderSentAt}). Bookings
 * made less than 24 h ahead are skipped: their confirmation email is still fresh. If the mail
 * server fails, the booking is released again and the next run (10 minutes later) retries.
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final MongoTemplate mongo;
    private final ArtistRepository artists;
    private final BookingService bookingService;
    private final BookingMailer mailer;
    private final BookingRules rules;
    private final Clock clock;

    public ReminderJob(MongoTemplate mongo, ArtistRepository artists, BookingService bookingService,
            BookingMailer mailer, BookingRules rules, Clock clock) {
        this.mongo = mongo;
        this.artists = artists;
        this.bookingService = bookingService;
        this.mailer = mailer;
        this.rules = rules;
        this.clock = clock;
    }

    /** Runs once when the server has started (catches up after downtime), then every 10 minutes. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 */10 * * * *")
    public void run() {
        int sent = sendDueReminders();
        if (sent > 0) {
            log.info("Sent {} booking reminder(s)", sent);
        }
    }

    /** Sends every reminder that is due now; returns how many. */
    public int sendDueReminders() {
        Instant now = clock.instant();
        Query due = Query.query(Criteria.where("status").is(BookingStatus.BOOKED)
                .and("email").ne(null)
                .and("reminderSentAt").is(null)
                .and("startsAt").gt(now).lte(now.plus(rules.reminderBefore())));
        int sent = 0;
        for (Booking booking : mongo.find(due, Booking.class)) {
            if (booking.getCreatedAt() != null
                    && booking.getCreatedAt().isAfter(booking.getStartsAt().minus(rules.reminderBefore()))) {
                continue; // booked less than a day ahead
            }
            // Claim it first, so a reminder is never sent twice
            Query unclaimed = Query.query(Criteria.where("_id").is(booking.getId())
                    .and("status").is(BookingStatus.BOOKED)
                    .and("reminderSentAt").is(null));
            boolean claimed = mongo.updateFirst(unclaimed, new Update().set("reminderSentAt", now), Booking.class)
                    .getModifiedCount() == 1;
            if (!claimed) {
                continue;
            }
            boolean delivered = mailer.sendReminder(
                    bookingService.toDto(booking, artists.findById(booking.getArtistId()).orElse(null)),
                    booking.getEmail());
            if (delivered) {
                sent++;
            } else {
                // Release it so the next run tries again
                mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.getId())),
                        new Update().unset("reminderSentAt"), Booking.class);
            }
        }
        return sent;
    }
}
