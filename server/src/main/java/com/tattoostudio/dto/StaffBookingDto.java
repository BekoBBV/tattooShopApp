package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;

/**
 * A booking as staff see it: full contact details (until they are wiped for privacy).
 *
 * @param depositOverdue   a deposit is still waiting after the time it was due (the admin's list highlights it)
 * @param awaitingStatus   the appointment is over but nobody has marked it Done or No-show yet
 * @param followsBookingId sessions: the consultation (or earlier session) it was reserved from
 */
public record StaffBookingDto(
        String id,
        String code,
        BookingType type,
        BookingStatus status,
        String title,
        String flashId,
        Integer widthCm,
        Integer heightCm,
        String artistId,
        String artistName,
        LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime time,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Instant startsAt,
        Instant endsAt,
        BigDecimal price,
        DepositDto deposit,
        boolean depositOverdue,
        String customerName,
        String phone,
        String email,
        LocalDate dateOfBirth,
        String notes,
        ConsultationDto consultation,
        String followsBookingId,
        boolean anonymized,
        boolean awaitingStatus,
        Instant createdAt) {
}
