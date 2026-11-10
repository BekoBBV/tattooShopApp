package com.tattoostudio.model;

/** What a booking is for. */
public enum BookingType {
    /** A ready-made flash design at a fixed price, booked online with a deposit. */
    FLASH,
    /** A free 30-minute talk with an artist to plan a custom piece (no deposit). */
    CONSULTATION,
    /** Time the artist reserves for a custom piece after a consultation, with its own price and deposit. */
    SESSION
}
