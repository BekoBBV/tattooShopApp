package com.tattoostudio.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Free start times for a service over several days.
 * {@code artistId} is null when the customer chose "any artist".
 */
public record AvailabilityDto(String serviceId, String artistId, List<Day> days) {

    /** One date and its free times; {@code slots} is empty when the day is closed or fully booked. */
    public record Day(LocalDate date, List<Slot> slots) {
    }

    /** One bookable start time; {@code freeArtists} is how many artists are free then. */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime time, Instant startsAt, int freeArtists) {
    }
}
