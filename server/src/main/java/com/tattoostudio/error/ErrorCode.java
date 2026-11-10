package com.tattoostudio.error;

/**
 * Stable, machine-readable error codes sent as "code" in every error response.
 * The frontend reacts to these, so the human-readable messages can change freely.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    ARTIST_NOT_FOUND,
    FLASH_NOT_FOUND,
    /** The flash design was just booked by someone else (or taken off the sheet). */
    FLASH_NOT_AVAILABLE,
    OUTSIDE_BOOKING_WINDOW,
    INVALID_PHONE,
    /** The customer is under 18 on the day of the appointment. */
    UNDER_AGE,
    PHONE_LIMIT_REACHED,
    /** The chosen time is not (or no longer) free: pick another time. */
    SLOT_UNAVAILABLE,
    BOOKING_NOT_FOUND,
    BOOKING_NOT_ACTIVE,
    /** The appointment has already started, so it can't be cancelled online. */
    CANCELLATION_CLOSED,
    /** The 15 minutes to pay the deposit ran out and the time was released: pick a time again. */
    HOLD_EXPIRED,
    /** There is no deposit waiting to be paid (already paid, or none needed). */
    NOTHING_TO_PAY,
    /** Only finished appointments can be reviewed, once each. */
    REVIEW_NOT_ALLOWED,
    /** A picture that is too large, not a JPG/PNG/HEIC photo, or too many of them. */
    INVALID_FILE,
    TOO_MANY_ATTEMPTS,
    /** Too many reviews, messages, bookings or accounts from one visitor this hour. */
    TOO_MANY_SUBMISSIONS,
    /** Wrong username or password. */
    INVALID_LOGIN,
    /** Missing, expired or invalid staff token. */
    UNAUTHORIZED,
    /** Signed in, but this needs another role (e.g. admin only). */
    FORBIDDEN,
    NOT_FOUND,
    /** The request conflicts with the current data, e.g. deactivating an artist with upcoming bookings. */
    CONFLICT
}
