package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.support.IntegrationTest;

class AvailabilityServiceTest extends IntegrationTest {

    @Autowired
    AvailabilityService availability;
    @Autowired
    BookingService bookingService;

    @Test
    void oneHourSlotsWithinWorkingHours() {
        List<Instant> free = availability.freeStarts(artist("Nia"), TUESDAY); // 10:00–20:00

        assertThat(free.get(0)).isEqualTo(at(TUESDAY, "10:00"));
        assertThat(free.get(free.size() - 1)).isEqualTo(at(TUESDAY, "19:00")); // 19:00 + 1 h = closing
        assertThat(free).hasSize(10);
    }

    @Test
    void noTimesOnADayOffOrWhenTheShopIsClosed() {
        assertThat(availability.freeStarts(artist("Jax"), TUESDAY)).isEmpty(); // Jax's day off
        assertThat(availability.freeStarts(artist("Nia"), MONDAY.plusWeeks(1))).isEmpty(); // shop closed
    }

    @Test
    void slotsCountFromTheArtistsStartTime() {
        Artist nia = artist("Nia");
        nia.getExceptions().add(DayException.customHours(TUESDAY, LocalTime.of(10, 30), LocalTime.of(13, 30), "Late start"));
        artists.save(nia);
        assertThat(availability.freeStarts(nia, TUESDAY))
                .containsExactly(at(TUESDAY, "10:30"), at(TUESDAY, "11:30"), at(TUESDAY, "12:30"));
    }

    @Test
    void timesOverlappingABookingAreNeverOffered() {
        book("Nia", "11:00"); // 11:00–12:00, made under the usual full-hour hours
        // Hours that start at half past (only possible in data saved before hours had to be on the hour)
        Artist nia = artist("Nia");
        nia.getExceptions().add(DayException.customHours(TUESDAY, LocalTime.of(10, 30), LocalTime.of(13, 30), "Late start"));
        artists.save(nia);
        // 10:30 and 11:30 would overlap the 11:00 booking
        assertThat(availability.freeStarts(nia, TUESDAY)).containsExactly(at(TUESDAY, "12:30"));
    }

    @Test
    void dayOffExceptionClosesTheDay() {
        Artist nia = artist("Nia");
        nia.getExceptions().add(DayException.dayOff(TUESDAY, "Holiday"));
        artists.save(nia);
        assertThat(availability.freeStarts(nia, TUESDAY)).isEmpty();
    }

    @Test
    void nothingSoonerThanOneHourFromNow() {
        clock.set(at(TUESDAY, "12:10"));
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY).get(0)).isEqualTo(at(TUESDAY, "14:00"));
    }

    @Test
    void nothingMoreThanThirtyDaysAhead() {
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY.plusWeeks(4))).isNotEmpty();
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY.plusWeeks(5))).isEmpty();
    }

    @Test
    void bookedSlotIsNoLongerFree() {
        book("Nia", "10:00");
        List<Instant> free = availability.freeStarts(artist("Nia"), TUESDAY);
        assertThat(free).doesNotContain(at(TUESDAY, "10:00"));
        assertThat(free.get(0)).isEqualTo(at(TUESDAY, "11:00"));
    }

    @Test
    void anyArtistCountsHowManyAreFree() {
        assertThat(anyArtist(TUESDAY).get(at(TUESDAY, "10:00"))).isEqualTo(5); // everyone except Jax

        book("Nia", "10:00");
        assertThat(anyArtist(TUESDAY).get(at(TUESDAY, "10:00"))).isEqualTo(4);
    }

    @Test
    void severalDaysAtOnceMatchOneDayAtATime() {
        book("Nia", "10:00");
        book("Rook", "15:00");
        var week = availability.freeStartsForDays(artist("Nia"), List.of(), MONDAY, 7);

        assertThat(week).hasSize(7);
        week.forEach((date, free) ->
                assertThat(free.keySet()).containsExactlyElementsOf(availability.freeStarts(artist("Nia"), date)));
        assertThat(anyArtist(TUESDAY).get(at(TUESDAY, "15:00"))).isEqualTo(4); // Rook booked
    }

    private TreeMap<Instant, Integer> anyArtist(LocalDate date) {
        return availability.freeStartsForDays(null, artists.findByActiveTrueOrderBySortOrderAsc(), date, 1).get(date);
    }

    private void book(String artist, String time) {
        bookingService.create(new CreateBookingRequest(service("Street Cut").getId(), artist(artist).getId(), TUESDAY,
                LocalTime.parse(time), "Test Customer", "0888123456", null, null));
    }
}
