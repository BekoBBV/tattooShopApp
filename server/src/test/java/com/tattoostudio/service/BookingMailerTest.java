package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.CreateConsultationRequest;
import com.tattoostudio.dto.CreateFlashBookingRequest;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.ColourChoice;
import com.tattoostudio.model.Placement;
import com.tattoostudio.support.IntegrationTest;

/** Booking emails, with a fake mail server that records what would be sent. */
class BookingMailerTest extends IntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    BookingService bookingService;

    @Test
    void aFlashBookingIsConfirmedOnceTheDepositIsPaid() {
        BookingDto held = bookingService.bookFlash(new CreateFlashBookingRequest(flash("Small dragon").getId(), TUESDAY,
                LocalTime.of(14, 0), "Ivan Petrov", "0888123456", "ivan@example.com", LocalDate.of(1990, 1, 1), null,
                true, true, true), null);
        verify(mailSender, after(300).never()).send(any(SimpleMailMessage.class)); // nothing while it is only held

        Booking booking = bookings.findByCode(held.code()).orElseThrow();
        bookingService.depositPaid(booking.getPaymentId());
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(sent.capture());
        SimpleMailMessage confirmation = sent.getValue();
        assertThat(confirmation.getTo()).containsExactly("ivan@example.com");
        assertThat(confirmation.getSubject()).contains(held.code());
        assertThat(confirmation.getText())
                .contains("Hi Ivan", held.code(), "Small dragon with Kai", "14:00 to 16:00", "€180", "€30", "€150",
                        "/manage", "48 hours");

        bookingService.cancel(bookings.findByCode(held.code()).orElseThrow(), false);
        verify(mailSender, timeout(2000).times(2)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).startsWith("Cancelled:");
        assertThat(sent.getValue().getText()).contains("can't be refunded"); // less than 48 hours ahead
    }

    @Test
    void consultationsAreConfirmedAtOnce() {
        bookingService.bookConsultation(new CreateConsultationRequest(artist("Nika").getId(), TUESDAY, LocalTime.of(12, 30),
                "Mira Petrova", "0888123456", "mira@example.com", LocalDate.of(1998, 3, 12), "A crane over waves",
                Placement.SHOULDER, 15, ColourChoice.NOT_SURE, true, true), List.of(), null);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).startsWith("Your consultation is booked");
        assertThat(sent.getValue().getText()).contains("Hi Mira", "Free consultation (30 minutes) with Nika", "12:30 to 13:00");
    }

    @Test
    void pricesKeepCentsOnlyWhenNeeded() {
        assertThat(BookingMailer.euros(new BigDecimal("34.00"))).isEqualTo("34");
        assertThat(BookingMailer.euros(new BigDecimal("28.5"))).isEqualTo("28.50");
    }
}
