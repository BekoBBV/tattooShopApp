package com.tattoostudio.service;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tattoostudio.repository.MessageRepository;

/**
 * Privacy clean-up for the contact form: messages (which contain names and contact
 * details) are deleted automatically once they are older than app.messages.keep-for (90 days).
 */
@Component
public class MessageRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionJob.class);

    private final MessageRepository messages;
    private final Duration keepFor;
    private final Clock clock;

    public MessageRetentionJob(MessageRepository messages, @Value("${app.messages.keep-for}") Duration keepFor,
            Clock clock) {
        this.messages = messages;
        this.keepFor = keepFor;
        this.clock = clock;
    }

    /** Runs once when the server has started, then every night at 03:00. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        long deleted = messages.deleteByCreatedAtBefore(clock.instant().minus(keepFor));
        if (deleted > 0) {
            log.info("Deleted {} contact message(s) older than {} days", deleted, keepFor.toDays());
        }
    }
}
