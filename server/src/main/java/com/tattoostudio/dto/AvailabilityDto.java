package com.tattoostudio.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Free start times with one artist for a booking of {@code durationMinutes} (a flash design's time or
 * a 30-minute consultation) over several days.
 */
public record AvailabilityDto(String artistId, String flashId, int durationMinutes, List<Day> days) {

    /**
     * One date and its free start times. {@code closed} means the artist doesn't work that day (e.g.
     * Mondays); an open day with no times is fully booked.
     */
    public record Day(LocalDate date, boolean closed, List<Slot> slots) {
    }

    /** One bookable start time. */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime time, Instant startsAt) {
    }
}
