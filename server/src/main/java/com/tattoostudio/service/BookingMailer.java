package com.tattoostudio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.config.ShopInfo;
import com.tattoostudio.dto.BookingDto;

/**
 * Emails customers their booking confirmation (with the booking code), a reminder the day before
 * (see ReminderJob) and cancellation notices.
 *
 * Sending happens in the background (@Async), so a slow or broken mail server never delays
 * or fails a booking. If no mail server is configured (spring.mail.host is empty), emails
 * are skipped and only logged.
 */
@Component
public class BookingMailer {

    private static final Logger log = LoggerFactory.getLogger(BookingMailer.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final ObjectProvider<JavaMailSender> sender;
    private final ShopInfo shop;
    private final BookingRules rules;
    private final String from;

    public BookingMailer(ObjectProvider<JavaMailSender> sender, ShopInfo shop, BookingRules rules,
            @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.shop = shop;
        this.rules = rules;
        this.from = from;
    }

    @Async
    public void bookingConfirmed(BookingDto booking, String email) {
        send(email, "Your " + shop.name() + " booking " + booking.code(), """
                Hi %s,

                Your chair is locked in. See you soon!

                  Booking code:  %s
                  When:          %s at %s
                  Service:       %s with %s
                  Price:         €%s (pay at the shop)
                  Where:         %s

                Need to cancel? You can do it online until %d hours before your appointment:
                %s/cancel
                You'll need your booking code and the phone number you booked with.

                %s · %s
                """.formatted(booking.customerName(), booking.code(), DATE.format(booking.date()), booking.time(),
                booking.serviceName(), booking.artistName(), euros(booking.price()),
                shop.address(), rules.cancelCutoff().toHours(), shop.websiteUrl(), shop.name(), shop.phone()));
    }

    /**
     * Sends the reminder right away (the reminder job already runs in the background) and returns
     * false if the mail server refused it, so the job can try again later.
     */
    public boolean sendReminder(BookingDto booking, String email) {
        return send(email, "Reminder: " + shop.name() + " on " + DATE.format(booking.date()) + " at " + booking.time(), """
                Hi %s,

                A quick reminder of your appointment:

                  When:          %s at %s
                  Service:       %s with %s
                  Price:         €%s (pay at the shop)
                  Where:         %s
                  Booking code:  %s

                Can't make it? Please cancel online until %d hours before, so someone else can take the chair:
                %s/cancel
                You'll need your booking code and the phone number you booked with.

                %s · %s
                """.formatted(booking.customerName(), DATE.format(booking.date()), booking.time(),
                booking.serviceName(), booking.artistName(), euros(booking.price()), shop.address(), booking.code(),
                rules.cancelCutoff().toHours(), shop.websiteUrl(), shop.name(), shop.phone()));
    }

    @Async
    public void bookingCancelled(BookingDto booking, String email) {
        send(email, "Cancelled: " + shop.name() + " booking " + booking.code(), """
                Hi %s,

                Your booking %s on %s at %s has been cancelled and the chair released.

                Book a new time whenever you're ready: %s

                %s · %s
                """.formatted(booking.customerName(), booking.code(), DATE.format(booking.date()), booking.time(),
                shop.websiteUrl(), shop.name(), shop.phone()));
    }

    /** 34 → "34", 28.5 → "28.50" */
    static String euros(BigDecimal amount) {
        BigDecimal plain = amount.stripTrailingZeros();
        return plain.scale() <= 0 ? plain.toPlainString() : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** @return false only if the mail server failed; "not configured" counts as done (nothing to retry) */
    private boolean send(String to, String subject, String text) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            log.info("Mail not configured, skipped email \"{}\"", subject);
            return true;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mail.send(message);
            log.info("Sent email \"{}\"", subject);
            return true;
        } catch (MailException e) {
            log.warn("Could not send email \"{}\": {}", subject, e.getMessage());
            return false;
        }
    }
}
