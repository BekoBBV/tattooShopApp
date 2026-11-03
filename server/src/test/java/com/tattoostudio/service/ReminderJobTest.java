package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.support.ApiTest;

/** Reminder emails 24 h before the appointment (it is Monday 08:00; bookings are on Tuesday). */
class ReminderJobTest extends ApiTest {

    @MockitoBean
    JavaMailSender mailSender;
    @Autowired
    ReminderJob reminders;

    @Test
    void remindsOnceADayBefore() {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED); // booked 26 h ahead

        assertThat(reminders.sendDueReminders()).isZero(); // more than 24 h to go

        clock.set(at(MONDAY, "10:30")); // 23.5 h before
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("ivan@example.com");
        assertThat(sent.getValue().getSubject()).startsWith("Reminder:").contains("at 10:00");
        assertThat(sent.getValue().getText())
                .contains("Hi Ivan Petrov", booking.getCode(), "Street Cut with Nia Banks", "/cancel", "2 hours");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getReminderSentAt()).isNotNull();

        clock.advance(Duration.ofMinutes(10));
        assertThat(reminders.sendDueReminders()).isZero(); // never twice
    }

    @Test
    void aFailedReminderIsTriedAgainNextRun() {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        clock.set(at(MONDAY, "10:30"));
        doThrow(new MailSendException("SMTP down")).doNothing().when(mailSender).send(any(SimpleMailMessage.class));

        assertThat(reminders.sendDueReminders()).isZero();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getReminderSentAt()).isNull();

        clock.advance(Duration.ofMinutes(10));
        assertThat(reminders.sendDueReminders()).isEqualTo(1);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getReminderSentAt()).isNotNull();
    }

    @Test
    void noReminderWithoutEmailOrWhenCancelledOrBookedLastMinute() {
        Booking noEmail = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        noEmail.setEmail(null);
        bookings.save(noEmail);
        booking("Sol", TUESDAY, "10:00", BookingStatus.CANCELLED);

        clock.set(at(MONDAY, "12:00"));
        booking("Melo", TUESDAY, "11:00", BookingStatus.BOOKED); // booked 23 h ahead: confirmation is enough

        clock.set(at(MONDAY, "18:00"));
        assertThat(reminders.sendDueReminders()).isZero();
        verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class));
    }
}
