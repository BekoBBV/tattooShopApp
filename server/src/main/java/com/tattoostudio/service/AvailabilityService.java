package com.tattoostudio.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.repository.BookingRepository;

/**
 * Works out when an artist is free. Every appointment takes one fixed slot (60 min by default).
 *
 * A start time is free when:
 * 1. it falls inside the artist's working hours for that date (a date exception wins over the weekly hours),
 *    counting in whole slots from the artist's start time (10:00, 11:00, 12:00…),
 * 2. the whole slot ends before closing,
 * 3. the artist has no active booking in that slot,
 * 4. it is at least 1 hour from now and at most 30 days ahead.
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

    /** A working period on one date, e.g. 10:00–20:00. */
    record Window(LocalTime start, LocalTime end) {
    }

    /** Free start times for one artist on one date, in time order. */
    public List<Instant> freeStarts(Artist artist, LocalDate date) {
        return freeStarts(artist, date, takenSlots(artist.getId(), date));
    }

    /**
     * Free start times for one artist, or for any artist when {@code artist} is null, for
     * {@code days} dates starting at {@code from}. Each time maps to how many artists are free then.
     * Loads the bookings for all artists and days with a single query.
     */
    public Map<LocalDate, TreeMap<Instant, Integer>> freeStartsForDays(Artist artist, List<Artist> allArtists,
            LocalDate from, int days) {
        List<Artist> artists = artist == null ? allArtists : List.of(artist);
        Map<String, List<Booking>> booked = activeBookings(artists, from, days);

        Map<LocalDate, TreeMap<Instant, Integer>> result = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            TreeMap<Instant, Integer> free = new TreeMap<>();
            for (Artist b : artists) {
                for (Instant start : freeStarts(b, date, slotsOf(booked.get(b.getId())))) {
                    free.merge(start, 1, Integer::sum);
                }
            }
            result.put(date, free);
        }
        return result;
    }

    /**
     * The artists free at {@code start}, least busy that day first (for "any artist").
     * Loads that day's bookings for all of them with a single query.
     */
    public List<Artist> freeAt(List<Artist> artists, Instant start) {
        LocalDate date = start.atZone(rules.timezone()).toLocalDate();
        Map<String, List<Booking>> booked = activeBookings(artists, date, 1);
        return artists.stream()
                .filter(b -> freeStarts(b, date, slotsOf(booked.get(b.getId()))).contains(start))
                .sorted(Comparator.comparingInt(b -> booked.getOrDefault(b.getId(), List.of()).size()))
                .toList();
    }

    /** Booked appointments of these artists on {@code days} dates from {@code from}, by artist id (one query). */
    private Map<String, List<Booking>> activeBookings(List<Artist> artists, LocalDate from, int days) {
        Instant rangeStart = from.atStartOfDay(rules.timezone()).toInstant();
        Instant rangeEnd = from.plusDays(days).atStartOfDay(rules.timezone()).toInstant();
        Map<String, List<Booking>> byArtist = new HashMap<>();
        for (Booking b : bookings.findActiveOverlapping(artists.stream().map(Artist::getId).toList(), rangeStart, rangeEnd)) {
            byArtist.computeIfAbsent(b.getArtistId(), id -> new ArrayList<>()).add(b);
        }
        return byArtist;
    }

    private static Set<Instant> slotsOf(List<Booking> booked) {
        Set<Instant> taken = new HashSet<>();
        if (booked != null) {
            booked.forEach(b -> taken.addAll(b.getSlots()));
        }
        return taken;
    }

    private List<Instant> freeStarts(Artist artist, LocalDate date, Set<Instant> taken) {
        if (!isWithinBookingWindow(date)) {
            return List.of();
        }
        Instant earliest = clock.instant().plus(rules.minNotice());

        int slotMin = (int) rules.slotLength().toMinutes();
        List<Instant> free = new ArrayList<>();
        for (Window window : workingWindows(artist, date)) {
            int openMin = window.start().toSecondOfDay() / 60;
            int closeMin = window.end().toSecondOfDay() / 60;
            // One slot after another from the start of the shift, as long as the slot ends by closing time
            for (int m = openMin; m + slotMin <= closeMin; m += slotMin) {
                Instant start = toInstant(date, LocalTime.ofSecondOfDay(m * 60L));
                if (!start.isBefore(earliest) && !overlapsTaken(start, taken)) {
                    free.add(start);
                }
            }
        }
        return free.stream().distinct().sorted().toList();
    }

    public boolean isFree(Artist artist, Instant start) {
        LocalDate date = start.atZone(rules.timezone()).toLocalDate();
        return freeStarts(artist, date).contains(start);
    }

    /**
     * True if a slot starting at {@code start} overlaps a taken slot. Hours are kept on the slot grid,
     * so normally this is an exact match; comparing ranges also covers bookings made under old hours.
     */
    private boolean overlapsTaken(Instant start, Set<Instant> taken) {
        Instant end = start.plus(rules.slotLength());
        for (Instant t : taken) {
            if (start.isBefore(t.plus(rules.slotLength())) && t.isBefore(end)) {
                return true;
            }
        }
        return false;
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

    public Instant endOf(Instant start) {
        return start.plus(rules.slotLength());
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

    private Set<Instant> takenSlots(String artistId, LocalDate date) {
        Instant dayStart = date.atStartOfDay(rules.timezone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(rules.timezone()).toInstant();
        return slotsOf(bookings.findByArtistIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
                artistId, BookingStatus.BOOKED, dayEnd, dayStart));
    }
}
