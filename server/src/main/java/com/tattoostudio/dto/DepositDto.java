package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.DepositMethod;
import com.tattoostudio.model.DepositStatus;

/**
 * A booking's deposit. {@code amount} is null when there is none (status NONE); {@code dueAt} is when
 * a waiting deposit must be paid (the end of the 15-minute hold for flash).
 */
public record DepositDto(BigDecimal amount, DepositStatus status, DepositMethod method, Instant dueAt, Instant paidAt,
        Instant refundedAt) {

    public static DepositDto from(Booking b) {
        return new DepositDto(b.getDepositAmount(), b.getDepositStatus(), b.getDepositMethod(), b.getDepositDueAt(),
                b.getDepositPaidAt(), b.getDepositRefundedAt());
    }
}
