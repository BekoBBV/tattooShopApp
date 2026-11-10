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
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositStatus;

/**
 * Emails customers their booking confirmation (with the booking code), the deposit to pay for a
 * session the artist reserved, a reminder the day before (see ReminderJob) and cancellation notices.
 *
 * Sending happens in the background (@Async), so a slow or broken mail server never delays or fails
 * a booking. If no mail server is configured (spring.mail.host is empty), emails are skipped and only
 * logged.
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

    /** A confirmed flash booking (deposit paid) or a booked consultation. */
    @Async
    public void bookingConfirmed(BookingDto b, String email) {
        if (email == null) {
            return;
        }
        boolean consultation = b.type() == BookingType.CONSULTATION;
        String what = consultation ? "Free consultation (30 minutes) with " + b.artistName()
                : b.title() + " with " + b.artistName();
        String money = consultation ? "" : """
                  Price:         €%s
                  Deposit paid:  €%s
                  Pay in studio: €%s
                """.formatted(euros(b.price()), euros(b.deposit().amount()), euros(b.leftToPay()));
        String before = consultation
                ? "Bring your ideas and any other references. Your artist will plan the piece and the sessions with you."
                : """
                Before you come: eat a proper meal, bring an ID, wear clothes that leave the placement free,
                and don't drink alcohol the day before.""";
        send(email, (consultation ? "Your consultation is booked: " : "You're booked: ") + b.code(), """
                Hi %s,

                %s

                  Booking code:  %s
                  When:          %s, %s to %s
                  What:          %s
                %s  Where:         %s

                %s

                %s
                Keep your booking code and phone number: you need both to manage or cancel the booking at
                %s/manage

                %s, %s
                """.formatted(firstName(b), consultation ? "Your consultation is booked." : "You're booked.", b.code(),
                DATE.format(b.date()), b.time(), b.endTime(), what, money, shop.address(), before, cancelRule(b),
                shop.websiteUrl(), shop.name(), shop.phone()));
    }

    /** The artist reserved a session: the customer pays the deposit online to secure it. */
    @Async
    public void sessionReserved(BookingDto b, String email) {
        String deposit = b.deposit().status() == DepositStatus.WAITING
                ? "Please pay the €" + euros(b.deposit().amount()) + " deposit to secure the session: open "
                        + shop.websiteUrl() + "/manage and enter your booking code and phone number."
                : "No deposit is needed for this session.";
        send(email, b.artistName() + " reserved your session: " + b.code(), """
                Hi %s,

                %s reserved time for your tattoo.

                  Booking code:  %s
                  What:          %s
                  When:          %s, %s to %s
                  Price:         €%s
                  Where:         %s

                %s

                %s

                %s, %s
                """.formatted(firstName(b), b.artistName(), b.code(), b.title(), DATE.format(b.date()), b.time(),
                b.endTime(), euros(b.price()), shop.address(), deposit, cancelRule(b), shop.name(), shop.phone()));
    }

    /**
     * Sends the reminder right away (the reminder job already runs in the background) and returns
     * false if the mail server refused it, so the job can try again later.
     */
    public boolean sendReminder(BookingDto b, String email) {
        String what = b.type() == BookingType.CONSULTATION ? "your consultation with " + b.artistName()
                : b.title() + " with " + b.artistName();
        return send(email, "Tomorrow at " + shop.name() + ": " + b.time(), """
                Hi %s,

                A quick reminder: %s, %s at %s.

                  Booking code:  %s
                  Where:         %s

                Can't make it? Please let us know as soon as you can: %s/manage
                %s

                %s, %s
                """.formatted(firstName(b), what, DATE.format(b.date()), b.time(), b.code(), shop.address(),
                shop.websiteUrl(), cancelRule(b), shop.name(), shop.phone()));
    }

    @Async
    public void bookingCancelled(BookingDto b, String email, boolean byStudio) {
        String deposit = switch (b.deposit().status()) {
            case REFUNDED -> "Your €" + euros(b.deposit().amount()) + " deposit will be refunded to your card.";
            case KEPT -> "It was less than " + rules.refundCutoff().toHours() + " hours before the appointment, so the €"
                    + euros(b.deposit().amount()) + " deposit can't be refunded.";
            default -> "";
        };
        send(email, "Cancelled: " + shop.name() + " booking " + b.code(), """
                Hi %s,

                %s %s on %s at %s has been cancelled. %s

                Book again whenever you're ready: %s

                %s, %s
                """.formatted(firstName(b), byStudio ? "We're sorry: the studio had to cancel your booking" : "Your booking",
                b.code(), DATE.format(b.date()), b.time(), deposit, shop.websiteUrl(), shop.name(), shop.phone()));
    }

    private String cancelRule(BookingDto b) {
        if (b.deposit().amount() == null) {
            return "Need to cancel? You can do it online until the appointment starts.";
        }
        return "Need to cancel? The deposit is refunded if you cancel at least " + rules.refundCutoff().toHours()
                + " hours before the appointment, and kept if you cancel later.";
    }

    private static String firstName(BookingDto b) {
        return BookingService.firstName(b.customerName());
    }

    /** 34 → "34", 28.5 → "28.50" */
    public static String euros(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
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
