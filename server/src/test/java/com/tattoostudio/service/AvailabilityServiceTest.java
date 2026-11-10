package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DayException;
import com.tattoostudio.support.ApiTest;

class AvailabilityServiceTest extends ApiTest {

    private static final Duration CONSULTATION = Duration.ofMinutes(30);
    private static final Duration TWO_HOURS = Duration.ofHours(2);

    @Autowired
    AvailabilityService availability;

    @Test
    void halfHourStartsWithinWorkingHours() {
        List<Instant> free = day("Nika", TUESDAY, CONSULTATION); // 11:00–23:00
        assertThat(free.get(0)).isEqualTo(at(TUESDAY, "11:00"));
        assertThat(free.get(1)).isEqualTo(at(TUESDAY, "11:30"));
        assertThat(free.get(free.size() - 1)).isEqualTo(at(TUESDAY, "22:30")); // ends at closing
        assertThat(free).hasSize(24);
    }

    @Test
    void longerBookingsMustEndByClosingTime() {
        List<Instant> free = day("Kai", TUESDAY, TWO_HOURS);
        assertThat(free.get(free.size() - 1)).isEqualTo(at(TUESDAY, "21:00"));
    }

    @Test
    void noTimesOnMondayOrADayOff() {
        assertThat(day("Nika", MONDAY.plusWeeks(1), CONSULTATION)).isEmpty(); // studio closed
        Artist nika = artist("Nika");
        nika.getExceptions().add(DayException.dayOff(TUESDAY, "Tattoo convention, Milan"));
        artists.save(nika);
        assertThat(day("Nika", TUESDAY, CONSULTATION)).isEmpty();
        assertThat(availability.worksOn(artist("Nika"), TUESDAY)).isFalse();
    }

    @Test
    void customHoursCountFromTheirStart() {
        Artist nika = artist("Nika");
        nika.getExceptions().add(DayException.customHours(TUESDAY, LocalTime.of(14, 30), LocalTime.of(16, 0), "Late start"));
        artists.save(nika);
        assertThat(day("Nika", TUESDAY, Duration.ofHours(1)))
                .containsExactly(at(TUESDAY, "14:30"), at(TUESDAY, "15:00"));
    }

    @Test
    void aBookingAndTheBreakAfterItAreTaken() {
        booking("Kai", TUESDAY, "14:00", 120, BookingType.FLASH, BookingStatus.BOOKED); // 14:00–16:00, break to 16:30
        List<Instant> free = day("Kai", TUESDAY, TWO_HOURS);
        // 11:30–13:30 plus its break ends by 14:00; 12:00 would run into the booking
        assertThat(free).contains(at(TUESDAY, "11:30"), at(TUESDAY, "16:30"))
                .doesNotContain(at(TUESDAY, "12:00"), at(TUESDAY, "14:00"), at(TUESDAY, "16:00"));
    }

    @Test
    void theBreakFollowsTheArtistsSetting() {
        Artist kai = artist("Kai");
        kai.setBreakMinutes(60);
        artists.save(kai);
        booking("Kai", TUESDAY, "14:00", 120, BookingType.FLASH, BookingStatus.BOOKED); // break of the old booking: 30 min
        List<Instant> free = day("Kai", TUESDAY, CONSULTATION);
        // A new consultation needs its own 60-minute break before the 14:00 booking
        assertThat(free).contains(at(TUESDAY, "12:30"), at(TUESDAY, "16:30"))
                .doesNotContain(at(TUESDAY, "13:00"), at(TUESDAY, "13:30"));
    }

    @Test
    void cancelledAndLapsedBookingsFreeTheirTime() {
        booking("Kai", TUESDAY, "14:00", 120, BookingType.FLASH, BookingStatus.CANCELLED);
        Booking held = booking("Kai", TUESDAY, "17:00", 120, BookingType.FLASH, BookingStatus.PENDING);
        held.setDepositDueAt(NOW.minusSeconds(1)); // 15 minutes ran out, the job hasn't run yet
        bookings.save(held);
        assertThat(day("Kai", TUESDAY, TWO_HOURS)).contains(at(TUESDAY, "14:00"), at(TUESDAY, "17:00"));
    }

    @Test
    void aRunningHoldKeepsItsTime() {
        Booking held = booking("Kai", TUESDAY, "17:00", 120, BookingType.FLASH, BookingStatus.PENDING);
        held.setDepositDueAt(NOW.plusSeconds(600));
        bookings.save(held);
        assertThat(day("Kai", TUESDAY, TWO_HOURS)).doesNotContain(at(TUESDAY, "17:00"));
    }

    @Test
    void nothingSoonerThanTwoHoursFromNow() {
        clock.set(at(TUESDAY, "12:10"));
        assertThat(day("Nika", TUESDAY, CONSULTATION).get(0)).isEqualTo(at(TUESDAY, "14:30"));
    }

    @Test
    void nothingMoreThanThirtyDaysAhead() {
        assertThat(day("Nika", TUESDAY.plusWeeks(4), CONSULTATION)).isNotEmpty();
        assertThat(day("Nika", TUESDAY.plusWeeks(5), CONSULTATION)).isEmpty();
    }

    @Test
    void severalDaysAtOnceMatchOneDayAtATime() {
        booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        booking("Nika", TUESDAY.plusDays(2), "15:00", BookingStatus.BOOKED);
        var week = availability.freeStartsForDays(artist("Nika"), Duration.ofHours(1), MONDAY, 7);
        assertThat(week).hasSize(7);
        week.forEach((date, free) -> free.forEach(start ->
                assertThat(availability.isFree(artist("Nika"), start, Duration.ofHours(1))).isTrue()));
        assertThat(week.get(TUESDAY)).doesNotContain(at(TUESDAY, "11:00"));
    }

    @Test
    void slotsCoverTheBookingAndItsBreak() {
        assertThat(availability.slotsFor(at(TUESDAY, "14:00"), at(TUESDAY, "15:00"), 30))
                .containsExactly(at(TUESDAY, "14:00"), at(TUESDAY, "14:30"), at(TUESDAY, "15:00"));
        assertThat(availability.onGrid(at(TUESDAY, "14:30"))).isTrue();
        assertThat(availability.onGrid(at(TUESDAY, "14:15"))).isFalse();
    }

    private List<Instant> day(String artist, LocalDate date, Duration length) {
        return availability.freeStartsForDays(artist(artist), length, date, 1).get(date);
    }
}
