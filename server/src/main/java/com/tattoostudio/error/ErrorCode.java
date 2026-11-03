package com.tattoostudio.error;

/**
 * Stable, machine-readable error codes sent as "code" in every error response.
 * The frontend reacts to these, so the human-readable messages can change freely.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    SERVICE_NOT_FOUND,
    ARTIST_NOT_FOUND,
    OUTSIDE_BOOKING_WINDOW,
    INVALID_PHONE,
    PHONE_LIMIT_REACHED,
    /** The chosen time is not (or no longer) free: pick another time. */
    SLOT_UNAVAILABLE,
    BOOKING_NOT_FOUND,
    BOOKING_NOT_ACTIVE,
    CANCELLATION_CLOSED,
    TOO_MANY_ATTEMPTS,
    /** Too many reviews or contact messages from one visitor this hour. */
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
