package com.tattoostudio.dto;

import java.time.LocalDate;

/**
 * A customer an artist can reserve a session for: someone who had a consultation or session with
 * them. {@code bookingId} is their latest one (pass it as customerBookingId); {@code about} is its
 * idea or title.
 */
public record CustomerOptionDto(String bookingId, String code, String name, String phone, String email,
        LocalDate lastVisit, String about) {
}
