package com.tattoostudio.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.BookingUpdateRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.util.Paging;
import com.tattoostudio.util.PhoneNumbers;

/**
 * Bookings as staff see and manage them. Artists only ever see and change their own
 * bookings; the admin sees everyone's.
 */
@Service
public class StaffBookingService {

    private static final Logger log = LoggerFactory.getLogger(StaffBookingService.class);
    /** Without a date range, the list shows the last week and everything bookable ahead. */
    private static final int DEFAULT_DAYS_BACK = 7;
    private static final int MAX_RANGE_DAYS = 366;

    private final BookingRepository bookings;
    private final ArtistRepository artists;
    private final MongoTemplate mongo;
    private final BookingService bookingService;
    private final BookingMailer mailer;
    private final BookingRules rules;
    private final Clock clock;

    public StaffBookingService(BookingRepository bookings, ArtistRepository artists, MongoTemplate mongo,
            BookingService bookingService, BookingMailer mailer, BookingRules rules, Clock clock) {
        this.bookings = bookings;
        this.artists = artists;
        this.mongo = mongo;
        this.bookingService = bookingService;
        this.mailer = mailer;
        this.rules = rules;
        this.clock = clock;
    }

    static final Map<String, String> BOOKING_SORTS = Map.of("date", "startsAt", "service", "serviceName",
            "customer", "customerName", "price", "priceAtBooking", "status", "status");

    /**
     * One page of bookings starting on dates from..to (inclusive, shop time), earliest first unless
     * sorted otherwise. {@code artistId} null means every artist (admin only); {@code status} null
     * means any status; the search text matches customer, phone, email, code, service and notes.
     */
    public PageDto<StaffBookingDto> list(String artistId, LocalDate from, LocalDate to, BookingStatus status,
            ListQuery list) {
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        LocalDate start = from != null ? from : today.minusDays(DEFAULT_DAYS_BACK);
        LocalDate end = to != null ? to : today.plusDays(rules.maxDaysAhead());
        if (end.isBefore(start) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Pick a date range of at most " + MAX_RANGE_DAYS + " days, with 'from' before 'to'");
        }
        Criteria criteria = Criteria.where("startsAt")
                .gte(start.atStartOfDay(rules.timezone()).toInstant())
                .lt(end.plusDays(1).atStartOfDay(rules.timezone()).toInstant());
        if (artistId != null) {
            criteria = criteria.and("artistId").is(artistId);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        List<Criteria> filters = Paging.filters();
        filters.add(criteria);
        if (list.text() != null) {
            filters.add(searchText(list.text()));
        }
        return Paging.find(mongo, Booking.class, filters, list, BOOKING_SORTS, "date,asc", page -> {
            Map<String, Artist> artistsById = artists.findAll().stream()
                    .collect(Collectors.toMap(Artist::getId, Function.identity()));
            return page.stream().map(b -> toDto(b, artistsById.get(b.getArtistId()))).toList();
        });
    }

    /** Customer name, phone (as typed or normalised), email, booking code, service or notes. */
    static Criteria searchText(String text) {
        List<Criteria> any = new ArrayList<>(List.of(
                Paging.contains(text, "customerName", "phone", "email", "code", "serviceName", "notes")));
        try {
            any.add(Paging.contains(PhoneNumbers.normalize(text), "phone")); // "0888 123 456" finds +359888123456
        } catch (IllegalArgumentException notAPhoneNumber) {
            // Only the plain text search
        }
        return new Criteria().orOperator(any);
    }

    /**
     * Marks a booking Done, No-show or Cancelled.
     * - Done / No-show only once the appointment has started; switching between the two fixes a mistake.
     * - Cancel only while the booking is still booked (the shop can cancel at any time, even last minute).
     * - A cancelled booking stays cancelled: its time may already be booked by someone else.
     */
    public StaffBookingDto updateStatus(String bookingId, BookingStatus newStatus, StaffUser user) {
        Booking booking = bookings.findById(bookingId)
                .filter(b -> user.isAdmin() || b.getArtistId().equals(user.artistId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));
        BookingStatus current = booking.getStatus();
        Instant now = clock.instant();

        switch (newStatus) {
            case BOOKED -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Status can be changed to DONE, NO_SHOW or CANCELLED");
            case DONE, NO_SHOW -> {
                if (current == BookingStatus.CANCELLED) {
                    throw notActive("This booking was cancelled");
                }
                if (booking.getStartsAt().isAfter(now)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                            "You can mark a booking done or no-show once the appointment has started");
                }
            }
            case CANCELLED -> {
                if (current != BookingStatus.BOOKED) {
                    throw notActive("Only booked appointments can be cancelled; this one is "
                            + current.name().toLowerCase().replace('_', '-'));
                }
            }
        }
        if (current == newStatus) {
            return toDto(booking, artists.findById(booking.getArtistId()).orElse(null));
        }
        Booking saved = bookingService.changeStatus(bookingId, current, newStatus)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                        "This booking was just changed by someone else. Reload and try again."));
        log.info("{} set {} to {}", user.username(), saved.getCode(), newStatus);

        Artist artist = artists.findById(saved.getArtistId()).orElse(null);
        // Let the customer know if the shop cancels an appointment that is still ahead
        if (newStatus == BookingStatus.CANCELLED && saved.getEmail() != null && saved.getStartsAt().isAfter(now)) {
            mailer.bookingCancelled(bookingService.toDto(saved, artist), saved.getEmail());
        }
        return toDto(saved, artist);
    }

    /** The admin corrects a booking's customer details and notes. Wiped (anonymised) bookings stay wiped. */
    public StaffBookingDto updateDetails(String bookingId, BookingUpdateRequest request) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));
        if (booking.isAnonymized()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "This booking's customer details were removed for privacy and can't be added back");
        }
        String phone;
        try {
            phone = PhoneNumbers.normalize(request.phone());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }
        // Only these fields are written, so a status change at the same moment is never undone
        Update update = new Update().set("customerName", request.customerName().trim()).set("phone", phone)
                .set("email", blankToNull(request.email())).set("notes", blankToNull(request.notes()));
        Booking saved = mongo.findAndModify(Query.query(Criteria.where("_id").is(bookingId).and("anonymized").is(false)),
                update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (saved == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This booking was just changed. Reload and try again.");
        }
        log.info("Admin edited the details of {}", saved.getCode());
        return toDto(saved, artists.findById(saved.getArtistId()).orElse(null));
    }

    /** Deletes a booking for good (e.g. a test or a duplicate); an upcoming one frees its time. */
    public void delete(String bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));
        bookings.delete(booking);
        log.info("Admin deleted booking {}", booking.getCode());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException notActive(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE, message);
    }

    StaffBookingDto toDto(Booking b, Artist artist) {
        ZonedDateTime local = b.getStartsAt().atZone(rules.timezone());
        boolean awaitingStatus = b.getStatus() == BookingStatus.BOOKED && !b.getEndsAt().isAfter(clock.instant());
        return new StaffBookingDto(
                b.getId(),
                b.getCode(),
                b.getStatus(),
                b.getServiceName(),
                b.getArtistId(),
                artist == null ? null : artist.getName(),
                local.toLocalDate(),
                local.toLocalTime(),
                b.getStartsAt(),
                b.getEndsAt(),
                b.getPriceAtBooking(),
                b.getCustomerName(),
                b.getPhone(),
                b.getEmail(),
                b.getNotes(),
                b.isAnonymized(),
                awaitingStatus);
    }
}
