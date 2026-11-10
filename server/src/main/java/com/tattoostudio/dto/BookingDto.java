package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;

/**
 * A booking as shown to the customer who made it (or that an artist reserved for them).
 *
 * @param title          the flash design's name or the session's description; null for consultations
 * @param leftToPay      what is paid in the studio: the price minus the deposit (null for consultations)
 * @param phoneMasked    null once the details are wiped for privacy
 * @param consultation   the idea and reference photos, for consultations only
 * @param cancellable    the customer can still cancel online (until the appointment starts)
 * @param refundDeadline cancelling until then refunds a paid deposit; later it is kept
 * @param refundIfCancelledNow cancelling now would refund a paid deposit
 * @param canPayDeposit  a deposit is waiting and can be paid online now
 * @param canReview      finished flash or session that hasn't been reviewed yet
 */
public record BookingDto(
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
        BigDecimal leftToPay,
        String customerName,
        String phoneMasked,
        ConsultationDto consultation,
        boolean cancellable,
        Instant refundDeadline,
        boolean refundIfCancelledNow,
        boolean canPayDeposit,
        boolean canReview) {
}
