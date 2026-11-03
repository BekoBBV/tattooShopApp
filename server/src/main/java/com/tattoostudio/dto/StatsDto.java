package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Shop numbers for bookings starting between from and to (inclusive). {@code bookings} counts
 * appointments that were not cancelled; revenue counts DONE bookings only (payment is at the shop).
 */
public record StatsDto(LocalDate from, LocalDate to, long bookings, long done, long noShows, long cancelled,
        BigDecimal revenue, BusiestArtist busiestArtist, List<ArtistStats> perArtist) {

    public record BusiestArtist(String id, String name, long bookings) {
    }

    public record ArtistStats(String artistId, String name, long bookings, long done, long noShows, BigDecimal revenue) {
    }
}
