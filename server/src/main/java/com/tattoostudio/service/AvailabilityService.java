package com.tattoostudio.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.repository.BookingRepository;

/**
 * Works out when an artist is free for a booking of a given length (a flash design's time, or a
 * 30-minute consultation).
 *
 * Everything runs on a 30-minute grid. A booking takes every grid step from its start until the
 * end of the artist's break after it (30 minutes by default), so the next client can start right
 * after the break. A start time is free when:
 * 1. it falls inside the artist's working hours for that date (a date exception wins over the weekly
 *    hours), counting in grid steps from the artist's start time, and the work ends by closing time
 *    (the break after the last client may run past it),
 * 2. none of the steps it needs, its break included, is taken by another booking,
 * 3. it is at least 2 hours from now and at most 30 days ahead.
 * A flash booking whose 15 minutes to pay the deposit have run out no longer counts as taken.
 */
@Service
public class AvailabilityService {

    private final BookingRepository bookings;
    private final BookingRules rules;
    private final Clock clock;

    public AvailabilityService(BookingRepository bookings, BookingRules rules, Clock clock) {
        this.bookings = bookings;
        this.rules = rules;
        this.clock = clock;
    }

    /** A working period on one date, e.g. 11:00–23:00. */
    record Window(LocalTime start, LocalTime end) {
    }

    /** Free start times for one artist on {@code days} dates from {@code from}, with one database query. */
    public Map<LocalDate, List<Instant>> freeStartsForDays(Artist artist, Duration length, LocalDate from, int days) {
        Set<Instant> taken = takenSlots(artist.getId(), from, days);
        Map<LocalDate, List<Instant>> result = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            result.put(date, freeStarts(artist, date, length, taken));
        }
        return result;
    }

    /** True if a customer can book this artist at {@code start} for {@code length}. */
    public boolean isFree(Artist artist, Instant start, Duration length) {
        LocalDate date = start.atZone(rules.timezone()).toLocalDate();
        return freeStarts(artist, date, length, takenSlots(artist.getId(), date, 1)).contains(start);
    }

    /**
     * True if none of {@code slots} is taken by another booking (artists reserving sessions may
     * pick any time, inside their hours or not, as long as it is free).
     */
    public boolean slotsFree(Artist artist, List<Instant> slots) {
        if (slots.isEmpty()) {
            return true;
        }
        LocalDate first = slots.get(0).atZone(rules.timezone()).toLocalDate();
        LocalDate last = slots.get(slots.size() - 1).atZone(rules.timezone()).toLocalDate();
        Set<Instant> taken = takenSlots(artist.getId(), first, (int) (last.toEpochDay() - first.toEpochDay()) + 1);
        return slots.stream().noneMatch(taken::contains);
    }

    private List<Instant> freeStarts(Artist artist, LocalDate date, Duration length, Set<Instant> taken) {
        if (!isWithinBookingWindow(date)) {
            return List.of();
        }
        Instant earliest = clock.instant().plus(rules.minNotice());
        long step = rules.slotLength().toMinutes();
        long lengthMin = length.toMinutes();
        List<Instant> free = new ArrayList<>();
        for (Window window : workingWindows(artist, date)) {
            long openMin = window.start().toSecondOfDay() / 60;
            long closeMin = window.end().equals(LocalTime.MAX) ? 24 * 60 : window.end().toSecondOfDay() / 60;
            for (long m = openMin; m + lengthMin <= closeMin; m += step) {
                Instant start = toInstant(date, LocalTime.ofSecondOfDay(m * 60));
                if (start.isBefore(earliest) || free.contains(start)) {
                    continue;
                }
                List<Instant> needed = slotsFor(start, start.plus(length), artist.getBreakMinutes());
                if (needed.stream().noneMatch(taken::contains)) {
                    free.add(start);
                }
            }
        }
        free.sort(null);
        return free;
    }

    /**
     * The grid steps a booking from {@code start} to {@code end} takes, the artist's break after it
     * included: 14:00–16:00 with a 30-minute break takes 14:00, 14:30, 15:00, 15:30 and 16:00.
     */
    public List<Instant> slotsFor(Instant start, Instant end, int breakMinutes) {
        Instant until = end.plus(Duration.ofMinutes(Math.max(0, breakMinutes)));
        List<Instant> slots = new ArrayList<>();
        for (Instant t = start; t.isBefore(until); t = t.plus(rules.slotLength())) {
            slots.add(t);
        }
        return slots;
    }

    /** True if {@code instant} lies on the time grid (e.g. 14:00 or 14:30 shop time). */
    public boolean onGrid(Instant instant) {
        ZonedDateTime local = instant.atZone(rules.timezone());
        long minutes = local.toLocalTime().toSecondOfDay() / 60;
        return local.getSecond() == 0 && local.getNano() == 0 && minutes % rules.slotLength().toMinutes() == 0;
    }

    /** The grid steps already taken on {@code days} dates from {@code from} (one query). */
    private Set<Instant> takenSlots(String artistId, LocalDate from, int days) {
        Instant rangeStart = from.atStartOfDay(rules.timezone()).toInstant();
        Instant rangeEnd = from.plusDays(days).atStartOfDay(rules.timezone()).toInstant();
        Instant now = clock.instant();
        Set<Instant> taken = new HashSet<>();
        for (Booking b : bookings.findHoldingTime(List.of(artistId), rangeStart, rangeEnd)) {
            if (!isLapsedHold(b, now)) {
                taken.addAll(b.getSlots());
            }
        }
        return taken;
    }

    /** A flash booking whose time to pay the deposit has run out (the hold job releases it soon). */
    public static boolean isLapsedHold(Booking b, Instant now) {
        return b.getStatus() == BookingStatus.PENDING && b.getDepositDueAt() != null && !b.getDepositDueAt().isAfter(now);
    }

    /** True if [start, end) lies inside the artist's working hours that day (date exceptions included). */
    public boolean withinWorkingHours(Artist artist, Instant start, Instant end) {
        ZonedDateTime from = start.atZone(rules.timezone());
        ZonedDateTime to = end.atZone(rules.timezone());
        if (!from.toLocalDate().equals(to.toLocalDate()) && !to.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return false;
        }
        LocalTime startTime = from.toLocalTime();
        LocalTime endTime = to.toLocalDate().equals(from.toLocalDate()) ? to.toLocalTime() : LocalTime.MAX;
        return workingWindows(artist, from.toLocalDate()).stream()
                .anyMatch(w -> !startTime.isBefore(w.start()) && !endTime.isAfter(w.end()));
    }

    public boolean isWithinBookingWindow(LocalDate date) {
        LocalDate today = today();
        return !date.isBefore(today) && !date.isAfter(today.plusDays(rules.maxDaysAhead()));
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(rules.timezone()));
    }

    public Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, rules.timezone()).toInstant();
    }

    /** True if the artist works at all on this date. */
    public boolean worksOn(Artist artist, LocalDate date) {
        return !workingWindows(artist, date).isEmpty();
    }

    /** Working hours on a date: the date exception if there is one, otherwise the weekly hours. */
    List<Window> workingWindows(Artist artist, LocalDate date) {
        Optional<DayException> exception = artist.getExceptions().stream()
                .filter(e -> date.equals(e.date()))
                .findFirst();
        if (exception.isPresent()) {
            DayException e = exception.get();
            return e.closed() || e.start() == null || e.end() == null
                    ? List.of()
                    : List.of(new Window(e.start(), e.end()));
        }
        List<Window> windows = new ArrayList<>();
        for (WorkingHours h : artist.getWeeklyHours()) {
            if (h.day() == date.getDayOfWeek() && h.start().isBefore(h.end())) {
                windows.add(new Window(h.start(), h.end()));
            }
        }
        return windows;
    }
}
