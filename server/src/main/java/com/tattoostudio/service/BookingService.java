package com.tattoostudio.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.util.Paging;
import com.tattoostudio.util.PhoneNumbers;

/**
 * Creating, looking up and cancelling bookings for customers: guests use code + phone,
 * customers with an account also see and cancel their own bookings from "My bookings".
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final ArtistRepository artists;
    private final AvailabilityService availability;
    private final BookingCodeGenerator codes;
    private final LookupAttemptLimiter limiter;
    private final BookingMailer mailer;
    private final BookingRules rules;
    private final Clock clock;
    private final MongoTemplate mongo;

    public BookingService(BookingRepository bookings, CatalogService catalog, ArtistRepository artists,
            AvailabilityService availability, BookingCodeGenerator codes, LookupAttemptLimiter limiter,
            BookingMailer mailer, BookingRules rules, Clock clock, MongoTemplate mongo) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.artists = artists;
        this.availability = availability;
        this.codes = codes;
        this.limiter = limiter;
        this.mailer = mailer;
        this.rules = rules;
        this.clock = clock;
        this.mongo = mongo;
    }

    public BookingDto create(CreateBookingRequest request) {
        return create(request, null);
    }

    /** @param userId the signed-in customer account making the booking, or null for a guest */
    public BookingDto create(CreateBookingRequest request, String userId) {
        ServiceItem service = catalog.activeService(request.serviceId());

        Instant start = availability.toInstant(request.date(), request.time());
        if (start.isBefore(clock.instant().plus(rules.minNotice())) || !availability.isWithinBookingWindow(request.date())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OUTSIDE_BOOKING_WINDOW, "Bookings are possible from "
                    + rules.minNotice().toMinutes() + " minutes ahead up to " + rules.maxDaysAhead() + " days ahead");
        }

        String phone;
        try {
            phone = PhoneNumbers.normalize(request.phone());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }

        // One booking at a time per phone number and per account, so two simultaneous requests can't
        // both pass the limits below (one server; the double-booking guard itself is in the database).
        // Always phone first, then account (separate lock sets), so two requests never wait on each other.
        synchronized (lockFor(phoneLocks, phone)) {
            if (userId == null) {
                return book(request, service, start, phone, null);
            }
            synchronized (lockFor(accountLocks, userId)) {
                return book(request, service, start, phone, userId);
            }
        }
    }

    /** Checks the per-phone and per-account limits, then saves the booking with the first free artist. */
    private BookingDto book(CreateBookingRequest request, ServiceItem service, Instant start, String phone, String userId) {
        // Stops one person (or a script) from reserving lots of slots without an account
        long upcoming = bookings.countByPhoneAndStatusAndStartsAtAfter(phone, BookingStatus.BOOKED, clock.instant());
        if (upcoming >= rules.maxUpcomingPerPhone()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PHONE_LIMIT_REACHED, "This phone number already has "
                    + rules.maxUpcomingPerPhone() + " upcoming bookings. Please cancel one first or call the shop.");
        }
        // An account can't get around that by typing other phone numbers
        if (userId != null && bookings.countByUserIdAndStatusAndStartsAtAfter(userId, BookingStatus.BOOKED,
                clock.instant()) >= rules.maxUpcomingPerAccount()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PHONE_LIMIT_REACHED, "Your account already has "
                    + rules.maxUpcomingPerAccount() + " upcoming bookings. Please cancel one first or call the shop.");
        }

        List<Artist> candidates = candidateArtists(request.artistId(), start);

        // Try each free artist in turn. If someone else grabbed the slot a moment ago,
        // the database's unique slot index rejects the save and we move on.
        for (Artist artist : candidates) {
            Booking booking = new Booking();
            booking.setServiceId(service.getId());
            booking.setServiceName(service.getName());
            booking.setPriceAtBooking(service.getPrice());
            booking.setArtistId(artist.getId());
            booking.setStartsAt(start);
            booking.setEndsAt(availability.endOf(start));
            booking.setSlots(List.of(start));
            booking.setCustomerName(request.customerName().trim());
            booking.setPhone(phone);
            booking.setEmail(blankToNull(request.email()));
            booking.setNotes(blankToNull(request.notes()));
            booking.setUserId(userId);
            booking.setCreatedAt(clock.instant());
            if (trySave(booking)) {
                log.info("Booked {} with {} at {}", booking.getCode(), artist.getName(), start);
                BookingDto dto = toDto(booking, artist);
                if (booking.getEmail() != null) {
                    mailer.bookingConfirmed(dto, booking.getEmail());
                }
                return dto;
            }
            log.info("Slot {} for {} was taken concurrently, trying next artist", start, artist.getName());
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE, "Sorry, that time was just taken. Please pick another time.");
    }

    /**
     * Finds the booking for a code + phone pair. Wrong code and wrong phone give the same
     * answer, so the response never reveals whether a code exists.
     */
    public BookingDto lookup(String code, String phone, String clientKey) {
        Booking booking = findForCustomer(code, phone, clientKey);
        return toDto(booking, artists.findById(booking.getArtistId()).orElse(null));
    }

    public BookingDto cancel(String code, String phone, String clientKey) {
        return cancel(findForCustomer(code, phone, clientKey));
    }

    /** Which of a customer's bookings to show: still to come, already over (or cancelled), or all. */
    public enum When { UPCOMING, PAST, ALL }

    static final Map<String, String> ACCOUNT_SORTS = Map.of("date", "startsAt", "service", "serviceName",
            "price", "priceAtBooking", "status", "status");

    /**
     * The bookings made from this customer account. The search text matches the service and the
     * booking code. Upcoming = booked and not over yet, earliest first; otherwise latest first.
     */
    public PageDto<BookingDto> forAccount(String userId, When when, BookingStatus status, ListQuery list) {
        Instant now = clock.instant();
        List<Criteria> filters = Paging.filters();
        filters.add(Criteria.where("userId").is(userId));
        if (when == When.UPCOMING) {
            filters.add(Criteria.where("status").is(BookingStatus.BOOKED).and("endsAt").gt(now));
        } else if (when == When.PAST) {
            filters.add(new Criteria().orOperator(Criteria.where("status").ne(BookingStatus.BOOKED),
                    Criteria.where("endsAt").lte(now)));
        }
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (list.text() != null) {
            filters.add(Paging.contains(list.text(), "serviceName", "code"));
        }
        return Paging.find(mongo, Booking.class, filters, list, ACCOUNT_SORTS,
                when == When.UPCOMING ? "date,asc" : "date,desc", page -> {
                    Map<String, Artist> artistById = artists.findAllById(page.stream().map(Booking::getArtistId).distinct().toList())
                            .stream().collect(Collectors.toMap(Artist::getId, b -> b));
                    return page.stream().map(b -> toDto(b, artistById.get(b.getArtistId()))).toList();
                });
    }

    /** Cancels one of the customer's own bookings (same rules as with code + phone). */
    public BookingDto cancelForAccount(String code, String userId) {
        Booking booking = bookings.findByCode(BookingCodeGenerator.normalize(code))
                .filter(b -> userId != null && userId.equals(b.getUserId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND,
                        "This booking isn't in your account"));
        return cancel(booking);
    }

    private BookingDto cancel(Booking booking) {
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE, "This booking is already " + booking.getStatus().name().toLowerCase().replace('_', '-'));
        }
        if (!clock.instant().isBefore(cancelDeadline(booking))) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CANCELLATION_CLOSED, "Online cancellation closes "
                    + rules.cancelCutoff().toHours() + " hours before the appointment. Please call the shop.");
        }
        Booking saved = changeStatus(booking.getId(), BookingStatus.BOOKED, BookingStatus.CANCELLED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE,
                        "This booking was just changed. Please look it up again."));
        log.info("Cancelled {}", saved.getCode());
        BookingDto dto = toDto(saved, artists.findById(saved.getArtistId()).orElse(null));
        if (saved.getEmail() != null) {
            mailer.bookingCancelled(dto, saved.getEmail());
        }
        return dto;
    }

    private Booking findForCustomer(String code, String phone, String clientKey) {
        if (limiter.isBlocked(clientKey)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many attempts. Please wait " + rules.lookupWindow().toMinutes() + " minutes and try again.");
        }
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumbers.normalize(phone);
        } catch (IllegalArgumentException e) {
            normalizedPhone = null;
        }
        String finalPhone = normalizedPhone;
        Booking booking = bookings.findByCode(BookingCodeGenerator.normalize(code))
                .filter(b -> finalPhone != null && finalPhone.equals(b.getPhone()))
                .orElse(null);
        if (booking == null) {
            limiter.recordFailure(clientKey);
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND, "No booking found for this code and phone number");
        }
        return booking;
    }

    /**
     * Sets a booking's status if it still has the {@code expected} one, and returns the updated
     * booking (empty if it changed meanwhile). Only the status fields are written, so a change
     * made at the same moment (e.g. the privacy job wiping customer details) is never undone.
     */
    public Optional<Booking> changeStatus(String bookingId, BookingStatus expected, BookingStatus newStatus) {
        Update update = new Update().set("status", newStatus);
        if (newStatus == BookingStatus.CANCELLED) {
            update.set("cancelledAt", clock.instant());
        }
        return Optional.ofNullable(mongo.findAndModify(
                Query.query(Criteria.where("_id").is(bookingId).and("status").is(expected)),
                update, FindAndModifyOptions.options().returnNew(true), Booking.class));
    }

    private final Object[] phoneLocks = locks(64);
    private final Object[] accountLocks = locks(64);

    private static Object[] locks(int count) {
        Object[] locks = new Object[count];
        for (int k = 0; k < count; k++) {
            locks[k] = new Object();
        }
        return locks;
    }

    /** Same key (phone number or account id), same lock; other keys rarely share one (chosen by hash). */
    private static Object lockFor(Object[] locks, String key) {
        return locks[Math.floorMod(key.hashCode(), locks.length)];
    }

    /** The requested artist, or for "any artist" every free artist, least busy that day first. */
    private List<Artist> candidateArtists(String artistId, Instant start) {
        if (artistId != null && !artistId.isBlank()) {
            Artist artist = catalog.activeArtist(artistId);
            if (!availability.isFree(artist, start)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE, "Sorry, that time is not available. Please pick another time.");
            }
            return List.of(artist);
        }
        List<Artist> free = availability.freeAt(catalog.activeArtists(), start);
        if (free.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE, "Sorry, no artist is free at that time. Please pick another time.");
        }
        return free;
    }

    /**
     * Saves the booking with a fresh code. Returns false if the time slot was taken meanwhile.
     * A clash on the booking code itself (very unlikely) is retried with a new code.
     */
    private boolean trySave(Booking booking) {
        for (int attempt = 0; attempt < 5; attempt++) {
            booking.setCode(codes.next());
            try {
                bookings.save(booking);
                return true;
            } catch (DuplicateKeyException e) {
                if (isSlotClash(e)) {
                    return false;
                }
                // Otherwise the random code already existed: loop and try a new one
            }
        }
        throw new IllegalStateException("Could not generate a unique booking code");
    }

    /** True if the unique slot index (see Booking) rejected the save, i.e. the time is taken. */
    private static boolean isSlotClash(DuplicateKeyException e) {
        return e.getMessage() != null && e.getMessage().contains("no_double_booking");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Instant cancelDeadline(Booking booking) {
        return booking.getStartsAt().minus(rules.cancelCutoff());
    }

    BookingDto toDto(Booking b, Artist artist) {
        ZonedDateTime local = b.getStartsAt().atZone(rules.timezone());
        Instant deadline = cancelDeadline(b);
        boolean cancellable = b.getStatus() == BookingStatus.BOOKED && clock.instant().isBefore(deadline);
        return new BookingDto(
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
                PhoneNumbers.mask(b.getPhone()),
                b.getNotes(),
                cancellable,
                deadline);
    }
}
