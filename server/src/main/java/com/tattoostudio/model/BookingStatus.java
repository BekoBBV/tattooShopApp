package com.tattoostudio.model;

public enum BookingStatus {
    /** A flash booking whose deposit is not paid yet: the time and the design are held until {@code depositDueAt}. */
    PENDING,
    /** Confirmed: the deposit is paid, or no deposit is needed, or the artist reserved the time. */
    BOOKED,
    CANCELLED,
    /** The deposit wasn't paid in time, so the time and the design were released again. */
    EXPIRED,
    DONE,
    NO_SHOW;

    /** True while the booking keeps its time from being booked again (see the indexes on Booking). */
    public boolean holdsTime() {
        return this != CANCELLED && this != EXPIRED;
    }

    /** True while a flash design stays off the sheet: held, booked or tattooed (a no-show puts it back). */
    public boolean claimsFlash() {
        return this == PENDING || this == BOOKED || this == DONE;
    }
}
