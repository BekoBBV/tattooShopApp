package com.tattoostudio.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.BookingUpdateRequest;
import com.tattoostudio.dto.ConsultationDto;
import com.tattoostudio.dto.CustomerOptionDto;
import com.tattoostudio.dto.DepositDto;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ReserveSessionRequest;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositMethod;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.storage.ImageUploads;
import com.tattoostudio.util.Paging;
import com.tattoostudio.util.PhoneNumbers;

/**
 * Bookings as staff see and manage them. Artists only ever see and change their own bookings and
 * reserve sessions for their own customers; the admin sees everyone's.
 */
@Service
public class StaffBookingService {

    private static final Logger log = LoggerFactory.getLogger(StaffBookingService.class);
    /** Without a date range, the list shows the last week and everything bookable ahead. */
    private static final int DEFAULT_DAYS_BACK = 7;
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_CUSTOMER_OPTIONS = 20;

    private final BookingRepository bookings;
    private final ArtistRepository artists;
    private final MongoTemplate mongo;
    private final BookingService bookingService;
    private final AvailabilityService availability;
    private final BookingMailer mailer;
    private final ImageUploads images;
    private final BookingRules rules;
    private final Clock clock;

    public StaffBookingService(BookingRepository bookings, ArtistRepository artists, MongoTemplate mongo,
            BookingService bookingService, AvailabilityService availability, BookingMailer mailer, ImageUploads images,
            BookingRules rules, Clock clock) {
        this.bookings = bookings;
        this.artists = artists;
        this.mongo = mongo;
        this.bookingService = bookingService;
        this.availability = availability;
        this.mailer = mailer;
        this.images = images;
        this.rules = rules;
        this.clock = clock;
    }

    static final Map<String, String> BOOKING_SORTS = Map.of("date", "startsAt", "title", "title", "type", "type",
            "customer", "customerName", "price", "price", "status", "status");

    /** Filters for the staff and admin booking lists; null means any. */
    public record Filter(String artistId, LocalDate from, LocalDate to, BookingStatus status, BookingType type,
            DepositStatus depositStatus, DepositMethod depositMethod) {
    }

    /**
     * One page of bookings starting on dates from..to (inclusive, shop time), earliest first unless
     * sorted otherwise. A null artist means every artist (admin only). The search text matches
     * customer, phone, email, code, title, idea and notes.
     */
    public PageDto<StaffBookingDto> list(Filter filter, ListQuery list) {
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        LocalDate start = filter.from() != null ? filter.from() : today.minusDays(DEFAULT_DAYS_BACK);
        LocalDate end = filter.to() != null ? filter.to() : today.plusDays(rules.maxDaysAhead());
        if (end.isBefore(start) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Pick a date range of at most " + MAX_RANGE_DAYS + " days, with 'from' before 'to'");
        }
        Criteria criteria = Criteria.where("startsAt")
                .gte(start.atStartOfDay(rules.timezone()).toInstant())
                .lt(end.plusDays(1).atStartOfDay(rules.timezone()).toInstant());
        if (filter.artistId() != null) {
            criteria = criteria.and("artistId").is(filter.artistId());
        }
        if (filter.status() != null) {
            criteria = criteria.and("status").is(filter.status());
        }
        if (filter.type() != null) {
            criteria = criteria.and("type").is(filter.type());
        }
        if (filter.depositStatus() != null) {
            criteria = criteria.and("depositStatus").is(filter.depositStatus());
        }
        if (filter.depositMethod() != null) {
            criteria = criteria.and("depositMethod").is(filter.depositMethod());
        }
        List<Criteria> filters = Paging.filters();
        filters.add(criteria);
        if (list.text() != null) {
            filters.add(searchText(list.text()));
        }
        return Paging.find(mongo, Booking.class, filters, list, BOOKING_SORTS, "date,asc", this::toDtos);
    }

    /** Customer name, phone (as typed or normalised), email, code, title, idea or notes. */
    static Criteria searchText(String text) {
        List<Criteria> any = new ArrayList<>(List.of(
                Paging.contains(text, "customerName", "phone", "email", "code", "title", "idea", "notes")));
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
     * - Cancel while held or booked, at any time; a paid deposit is refunded (the studio cancelled).
     * - A cancelled booking stays cancelled: its time may already be booked by someone else.
     */
    public StaffBookingDto updateStatus(String bookingId, BookingStatus newStatus, StaffUser user) {
        Booking booking = visible(bookingId, user);
        BookingStatus current = booking.getStatus();
        switch (newStatus) {
            case DONE, NO_SHOW -> {
                if (current != BookingStatus.BOOKED && current != BookingStatus.DONE && current != BookingStatus.NO_SHOW) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE, "This booking is "
                            + current.name().toLowerCase().replace('_', '-'));
                }
                if (booking.getStartsAt().isAfter(clock.instant())) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                            "You can mark a booking done or no-show once the appointment has started");
                }
            }
            case CANCELLED -> {
                if (current == BookingStatus.CANCELLED) {
                    return toDto(booking);
                }
                Booking saved = bookingService.cancel(booking, true);
                log.info("{} cancelled {}", user.username(), saved.getCode());
                return toDto(saved);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Status can be changed to DONE, NO_SHOW or CANCELLED");
        }
        if (current == newStatus) {
            return toDto(booking);
        }
        Booking saved = bookingService.changeStatus(booking, newStatus)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                        "This booking was just changed by someone else. Reload and try again."));
        log.info("{} set {} to {}", user.username(), saved.getCode(), newStatus);
        return toDto(saved);
    }

    /** The admin records a deposit paid in the studio; a held flash booking is confirmed by it. */
    public StaffBookingDto depositPaidInCash(String bookingId) {
        Booking booking = visible(bookingId, null);
        if (booking.getDepositStatus() != DepositStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOTHING_TO_PAY, "This booking has no deposit waiting");
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE, "This booking is no longer active");
        }
        Update update = new Update().set("status", BookingStatus.BOOKED).set("holdsTime", true)
                .set("claimsFlash", booking.getType() == BookingType.FLASH)
                .set("depositStatus", DepositStatus.PAID).set("depositMethod", DepositMethod.CASH)
                .set("depositPaidAt", clock.instant());
        Booking saved = mongo.findAndModify(Query.query(Criteria.where("_id").is(bookingId)
                .and("depositStatus").is(DepositStatus.WAITING).and("status").in(BookingStatus.PENDING, BookingStatus.BOOKED)),
                update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (saved == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This booking was just changed. Reload and try again.");
        }
        log.info("Deposit for {} paid in cash", saved.getCode());
        if (booking.getStatus() == BookingStatus.PENDING) {
            mailer.bookingConfirmed(bookingService.toDto(saved), saved.getEmail());
        }
        return toDto(saved);
    }

    /**
     * An artist reserves time for a custom piece after a consultation. The time is taken from online
     * booking at once; the customer sees the session in their bookings and gets an email to pay the
     * deposit (72 hours, at most until the session).
     */
    public StaffBookingDto reserveSession(String artistId, ReserveSessionRequest request) {
        Artist artist = artists.findById(artistId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
        Booking from = bookings.findById(request.customerBookingId())
                .filter(b -> b.getArtistId().equals(artistId) && b.getType() != BookingType.FLASH && !b.isAnonymized())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND,
                        "Pick a customer from your consultations"));
        Instant now = clock.instant();
        Instant start = availability.toInstant(request.date(), request.start());
        Instant end = availability.toInstant(request.date(), request.end());
        if (!end.isAfter(start)) {
            throw invalid("The end time must be after the start time");
        }
        if (!availability.onGrid(start) || !availability.onGrid(end)) {
            throw invalid("Start and end times go in steps of " + rules.slotLength().toMinutes() + " minutes, e.g. 11:00 or 11:30");
        }
        if (!start.isAfter(now) || request.date().isAfter(availability.today().plusDays(rules.maxSessionDaysAhead()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OUTSIDE_BOOKING_WINDOW,
                    "Sessions can be reserved from now up to " + rules.maxSessionDaysAhead() + " days ahead");
        }
        if (request.deposit().compareTo(request.price()) > 0) {
            throw invalid("The deposit can't be more than the price");
        }
        List<Instant> slots = availability.slotsFor(start, end, artist.getBreakMinutes());
        // Release lapsed flash holds first: they no longer count as taken, but still hold the database index
        bookingService.expireHolds();
        if (!availability.slotsFree(artist, slots)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE,
                    "Part of this time is already booked (breaks between clients included)");
        }
        Booking session = new Booking();
        session.setType(BookingType.SESSION);
        session.changeStatus(BookingStatus.BOOKED);
        session.setTitle(request.description().trim());
        session.setPrice(request.price());
        if (request.deposit().signum() > 0) {
            session.setDepositAmount(request.deposit());
            session.setDepositStatus(DepositStatus.WAITING);
            Instant due = now.plus(rules.sessionDepositDue());
            session.setDepositDueAt(due.isAfter(start) ? start : due);
            session.setPaymentId(bookingService.newPaymentId());
        }
        session.setArtistId(artistId);
        session.setStartsAt(start);
        session.setEndsAt(end);
        session.setSlots(slots);
        session.setCustomerName(from.getCustomerName());
        session.setPhone(from.getPhone());
        session.setEmail(from.getEmail());
        session.setDateOfBirth(from.getDateOfBirth());
        session.setConsentAt(from.getConsentAt());
        session.setUserId(from.getUserId());
        session.setFollowsBookingId(from.getId());
        session.setCreatedAt(now);
        bookingService.saveWithCode(session);
        log.info("{} reserved session {} on {}", artist.getName(), session.getCode(), start);
        if (session.getEmail() != null) {
            mailer.sessionReserved(bookingService.toDto(session, artist), session.getEmail());
        }
        return toDto(session, artist);
    }

    /**
     * Customers this artist can reserve a session for: people from their consultations and sessions
     * (not yet wiped for privacy), one entry per phone number with their latest booking, matching the
     * search text (name, phone, email).
     */
    public List<CustomerOptionDto> customers(String artistId, String text) {
        Criteria criteria = Criteria.where("artistId").is(artistId).and("type").in(BookingType.CONSULTATION, BookingType.SESSION)
                .and("anonymized").is(false).and("status").nin(BookingStatus.EXPIRED);
        Query query = Query.query(text == null ? criteria : new Criteria().andOperator(criteria, searchText(text)))
                .with(Sort.by(Sort.Direction.DESC, "startsAt")).limit(500);
        Map<String, Booking> latestByPhone = new LinkedHashMap<>();
        for (Booking b : mongo.find(query, Booking.class)) {
            if (b.getPhone() != null) {
                latestByPhone.putIfAbsent(b.getPhone(), b);
            }
        }
        return latestByPhone.values().stream()
                .sorted(Comparator.comparing(Booking::getCustomerName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_CUSTOMER_OPTIONS)
                .map(b -> new CustomerOptionDto(b.getId(), b.getCode(), b.getCustomerName(), b.getPhone(), b.getEmail(),
                        b.getStartsAt().atZone(rules.timezone()).toLocalDate(),
                        b.getType() == BookingType.CONSULTATION ? b.getIdea() : b.getTitle()))
                .toList();
    }

    /** The admin corrects a booking's customer details and notes. Wiped (anonymised) bookings stay wiped. */
    public StaffBookingDto updateDetails(String bookingId, BookingUpdateRequest request) {
        Booking booking = visible(bookingId, null);
        if (booking.isAnonymized()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "This booking's customer details were removed for privacy and can't be added back");
        }
        String phone = BookingService.normalizePhone(request.phone());
        // Only these fields are written, so a status change at the same moment is never undone
        Update update = new Update().set("customerName", request.customerName().trim()).set("phone", phone)
                .set("email", blankToNull(request.email())).set("notes", blankToNull(request.notes()));
        Booking saved = mongo.findAndModify(Query.query(Criteria.where("_id").is(bookingId).and("anonymized").is(false)),
                update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (saved == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This booking was just changed. Reload and try again.");
        }
        log.info("Admin edited the details of {}", saved.getCode());
        return toDto(saved);
    }

    /** Deletes a booking for good (e.g. a test or a duplicate), with its reference photos; an upcoming one frees its time. */
    public void delete(String bookingId) {
        Booking booking = visible(bookingId, null);
        bookings.delete(booking);
        images.deleteAll(booking.getPhotos());
        log.info("Admin deleted booking {}", booking.getCode());
    }

    /** The booking, if this staff member may see it (null user: the admin). */
    private Booking visible(String bookingId, StaffUser user) {
        return bookings.findById(bookingId)
                .filter(b -> user == null || user.isAdmin() || b.getArtistId().equals(user.artistId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND, "Booking not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    /** One page of bookings with their artists (one query). */
    List<StaffBookingDto> toDtos(List<Booking> page) {
        Map<String, Artist> byId = artists.findAllById(page.stream().map(Booking::getArtistId).distinct().toList())
                .stream().collect(Collectors.toMap(Artist::getId, Function.identity()));
        return page.stream().map(b -> toDto(b, byId.get(b.getArtistId()))).toList();
    }

    StaffBookingDto toDto(Booking b) {
        return toDto(b, artists.findById(b.getArtistId()).orElse(null));
    }

    StaffBookingDto toDto(Booking b, Artist artist) {
        Instant now = clock.instant();
        ZonedDateTime start = b.getStartsAt().atZone(rules.timezone());
        ZonedDateTime end = b.getEndsAt().atZone(rules.timezone());
        boolean active = b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.BOOKED;
        boolean overdue = active && b.getDepositStatus() == DepositStatus.WAITING && b.getDepositDueAt() != null
                && !b.getDepositDueAt().isAfter(now);
        return new StaffBookingDto(
                b.getId(),
                b.getCode(),
                b.getType(),
                b.getStatus(),
                b.getTitle(),
                b.getFlashId(),
                b.getWidthCm(),
                b.getHeightCm(),
                b.getArtistId(),
                artist == null ? null : artist.getName(),
                start.toLocalDate(),
                start.toLocalTime(),
                end.toLocalTime(),
                b.getStartsAt(),
                b.getEndsAt(),
                b.getPrice(),
                DepositDto.from(b),
                overdue,
                b.getCustomerName(),
                b.getPhone(),
                b.getEmail(),
                b.getDateOfBirth(),
                b.getNotes(),
                b.getType() == BookingType.CONSULTATION ? ConsultationDto.from(b) : null,
                b.getFollowsBookingId(),
                b.isAnonymized(),
                b.getStatus() == BookingStatus.BOOKED && !b.getEndsAt().isAfter(now),
                b.getCreatedAt());
    }
}
