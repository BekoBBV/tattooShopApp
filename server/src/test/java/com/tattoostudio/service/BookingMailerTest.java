package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.support.IntegrationTest;

/** Booking emails, with a fake mail server that records what would be sent. */
class BookingMailerTest extends IntegrationTest {

    @MockitoBean
    JavaMailSender mailSender;
    @Autowired
    BookingService bookingService;

    @Test
    void confirmationAndCancellationAreEmailed() {
        BookingDto booking = book("ivan@example.com");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(2000)).send(sent.capture());
        SimpleMailMessage confirmation = sent.getValue();
        assertThat(confirmation.getTo()).containsExactly("ivan@example.com");
        assertThat(confirmation.getSubject()).contains(booking.code());
        assertThat(confirmation.getText())
                .contains("Hi Ivan", booking.code(), "Skin Fade with Nia Banks", "€34", "2 hours", "/cancel");

        bookingService.cancel(booking.code(), "0888123456", "mail-test");
        verify(mailSender, timeout(2000).times(2)).send(sent.capture());
        assertThat(sent.getValue().getSubject()).startsWith("Cancelled:");
    }

    @Test
    void noEmailWithoutAnAddress() {
        book(null);
        verify(mailSender, after(500).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void pricesKeepCentsOnlyWhenNeeded() {
        assertThat(BookingMailer.euros(new java.math.BigDecimal("34.00"))).isEqualTo("34");
        assertThat(BookingMailer.euros(new java.math.BigDecimal("28.5"))).isEqualTo("28.50");
    }

    private BookingDto book(String email) {
        return bookingService.create(new CreateBookingRequest(service("Skin Fade").getId(), artist("Nia").getId(),
                TUESDAY, LocalTime.of(10, 0), "Ivan", "0888123456", email, null));
    }
}
