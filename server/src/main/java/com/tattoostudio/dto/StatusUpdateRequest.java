package com.tattoostudio.dto;

import com.tattoostudio.model.BookingStatus;

import jakarta.validation.constraints.NotNull;

/** New status for a booking: DONE, NO_SHOW or CANCELLED. */
public record StatusUpdateRequest(@NotNull BookingStatus status) {
}
