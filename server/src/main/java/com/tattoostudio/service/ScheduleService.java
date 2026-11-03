package com.tattoostudio.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.ScheduleDto;
import com.tattoostudio.dto.ScheduleResponse;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.repository.ArtistRepository;

/**
 * An artist's working hours and date exceptions. Changing them never touches bookings
 * that already exist; it only changes which times can be booked from now on. Upcoming bookings
 * that no longer fit are reported as conflicts, so the artist or admin can decide what to do.
 */
@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    private static final int MAX_NOTE_LENGTH = 100;

    private final ArtistRepository artists;
    private final BookingRules rules;
    private final Clock clock;
    private final MongoTemplate mongo;
    private final BookingRepository bookings;
    private final AvailabilityService availability;
    private final StaffBookingService staffBookings;

    public ScheduleService(ArtistRepository artists, BookingRules rules, Clock clock, MongoTemplate mongo,
            BookingRepository bookings, AvailabilityService availability, StaffBookingService staffBookings) {
        this.artists = artists;
        this.mongo = mongo;
        this.bookings = bookings;
        this.availability = availability;
        this.staffBookings = staffBookings;
        this.rules = rules;
        this.clock = clock;
    }

    public ScheduleResponse get(String artistId) {
        Artist artist = artist(artistId);
        return new ScheduleResponse(artist.getWeeklyHours(), upcoming(artist.getExceptions()), conflicts(artist));
    }

    /** Upcoming bookings of this artist that fall outside their current hours. */
    public List<StaffBookingDto> conflicts(Artist artist) {
        return conflicts(List.of(artist)).getOrDefault(artist.getId(), List.of());
    }

    /** The same for several artists with one database query, by artist id. */
    public Map<String, List<StaffBookingDto>> conflicts(List<Artist> artistList) {
        Map<String, Artist> byId = artistList.stream().collect(Collectors.toMap(Artist::getId, b -> b));
        return bookings.findByArtistIdInAndStatusAndStartsAtAfterOrderByStartsAtAsc(byId.keySet(), BookingStatus.BOOKED,
                        clock.instant())
                .stream()
                .filter(b -> !availability.withinWorkingHours(byId.get(b.getArtistId()), b.getStartsAt(), b.getEndsAt()))
                .collect(Collectors.groupingBy(Booking::getArtistId,
                        Collectors.mapping(b -> staffBookings.toDto(b, byId.get(b.getArtistId())), Collectors.toList())));
    }

    public ScheduleResponse update(String artistId, ScheduleDto schedule) {
        Artist artist = artist(artistId);
        artist.setWeeklyHours(cleanWeeklyHours(schedule.weeklyHours()));
        artist.setExceptions(cleanExceptions(schedule.exceptions()));
        // Only the schedule is written, so a profile edit by the admin at the same time is kept
        mongo.updateFirst(Query.query(Criteria.where("_id").is(artistId)),
                new Update().set("weeklyHours", artist.getWeeklyHours()).set("exceptions", artist.getExceptions()),
                Artist.class);
        log.info("Updated schedule of {}", artist.getName());
        return new ScheduleResponse(artist.getWeeklyHours(), artist.getExceptions(), conflicts(artist));
    }

    private Artist artist(String artistId) {
        return artists.findById(artistId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
    }

    /**
     * Checks weekly hours: one entry per working day, start before end, start on the slot grid.
     * Sorted Monday to Sunday.
     */
    public List<WorkingHours> cleanWeeklyHours(List<WorkingHours> hours) {
        if (hours == null) {
            return new ArrayList<>();
        }
        Set<DayOfWeek> seen = new HashSet<>();
        for (WorkingHours h : hours) {
            if (h == null || h.day() == null || h.start() == null || h.end() == null) {
                throw invalid("Each working day needs a day, a start and an end time");
            }
            if (!h.start().isBefore(h.end())) {
                throw invalid("On " + dayName(h.day()) + " the start time must be before the end time");
            }
            requireOnSlotGrid(h.start(), "On " + dayName(h.day()));
            if (!seen.add(h.day())) {
                throw invalid(dayName(h.day()) + " is listed twice");
            }
        }
        List<WorkingHours> sorted = new ArrayList<>(hours);
        sorted.sort(Comparator.comparing(WorkingHours::day));
        return sorted;
    }

    /**
     * Checks date exceptions: one per date, custom hours need start before end, notes are
     * short. Past dates are dropped since they no longer matter. Sorted by date.
     */
    public List<DayException> cleanExceptions(List<DayException> exceptions) {
        if (exceptions == null) {
            return new ArrayList<>();
        }
        Set<LocalDate> seen = new HashSet<>();
        List<DayException> cleaned = new ArrayList<>();
        for (DayException e : exceptions) {
            if (e == null || e.date() == null) {
                throw invalid("Each exception needs a date");
            }
            if (!seen.add(e.date())) {
                throw invalid(e.date() + " is listed twice");
            }
            String note = e.note() == null || e.note().isBlank() ? null : e.note().trim();
            if (note != null && note.length() > MAX_NOTE_LENGTH) {
                throw invalid("Notes can be at most " + MAX_NOTE_LENGTH + " characters");
            }
            if (e.closed()) {
                cleaned.add(DayException.dayOff(e.date(), note));
            } else {
                if (e.start() == null || e.end() == null || !e.start().isBefore(e.end())) {
                    throw invalid("Custom hours on " + e.date() + " need a start time before the end time");
                }
                requireOnSlotGrid(e.start(), "On " + e.date());
                cleaned.add(DayException.customHours(e.date(), e.start(), e.end(), note));
            }
        }
        return upcoming(cleaned);
    }

    /** Exceptions from today on, sorted by date. */
    public List<DayException> upcoming(List<DayException> exceptions) {
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        return exceptions.stream()
                .filter(e -> !e.date().isBefore(today))
                .sorted(Comparator.comparing(DayException::date))
                .toList();
    }

    /**
     * Appointments start every slot length from the start time. Keeping every start time on the same
     * grid (the full hour for 60-minute slots) means existing bookings always line up with new hours,
     * so changing hours can never open a time that overlaps a booking.
     */
    private void requireOnSlotGrid(LocalTime start, String where) {
        long slotMinutes = rules.slotLength().toMinutes();
        if ((start.toSecondOfDay() / 60) % slotMinutes != 0 || start.getSecond() != 0) {
            throw invalid(where + " the start time must be "
                    + (slotMinutes == 60 ? "on the full hour (e.g. 10:00)" : "a multiple of " + slotMinutes + " minutes")
                    + ", so appointments line up");
        }
    }

    private static String dayName(DayOfWeek day) {
        String name = day.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}
