package com.tattoostudio.model;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Overrides an artist's weekly hours on one date: either a day off
 * ({@code closed = true}) or custom hours for that day.
 */
public record DayException(
        LocalDate date,
        boolean closed,
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end,
        String note) {

    public static DayException dayOff(LocalDate date, String note) {
        return new DayException(date, true, null, null, note);
    }

    public static DayException customHours(LocalDate date, LocalTime start, LocalTime end, String note) {
        return new DayException(date, false, start, end, note);
    }
}
