package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.tattoostudio.model.BookingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A booking as shown to the customer who made it. The phone number is masked
 * (null once the details are wiped), and {@code cancellable} says whether online
 * cancellation is still open.
 */
public record BookingDto(
        String code,
        BookingStatus status,
        String serviceName,
        String artistId,
        String artistName,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        Instant startsAt,
        Instant endsAt,
        BigDecimal price,
        String customerName,
        String phoneMasked,
        String notes,
        boolean cancellable,
        Instant cancelDeadline) {
}
