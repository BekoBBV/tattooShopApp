package com.tattoostudio.model;

public enum DepositStatus {
    /** No deposit for this booking (consultations, or a session the artist reserved without one). */
    NONE,
    WAITING,
    PAID,
    /** Paid and given back: cancelled at least 48 hours ahead, or cancelled by the studio. */
    REFUNDED,
    /** Paid and kept: the customer cancelled less than 48 hours ahead. */
    KEPT,
    /** Never paid: the hold expired or the booking was cancelled before paying. */
    NOT_PAID
}
