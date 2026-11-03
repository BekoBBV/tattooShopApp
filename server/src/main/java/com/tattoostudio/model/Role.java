package com.tattoostudio.model;

public enum Role {
    /** The owner: sees and manages everything. */
    ADMIN,
    /** An artist: sees and manages only their own appointments and hours. */
    ARTIST,
    /** A customer with an account: books with their details filled in and sees their own bookings. */
    USER
}
