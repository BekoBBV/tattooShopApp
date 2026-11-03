package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.tattoostudio.model.BookingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * A booking as staff see it: full contact details (until they are wiped 24 h after the
 * appointment). {@code awaitingStatus} means the appointment is over but nobody has marked
 * it Done or No-show yet.
 */
public record StaffBookingDto(
        String id,
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
        String phone,
        String email,
        String notes,
        boolean anonymized,
        boolean awaitingStatus) {
}
