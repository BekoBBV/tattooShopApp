package com.tattoostudio.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.ConsultationDto;
import com.tattoostudio.dto.CreateConsultationRequest;
import com.tattoostudio.dto.CreateFlashBookingRequest;
import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.DepositDto;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.PaymentStartDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositMethod;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Review;
import com.tattoostudio.payment.PaymentProvider;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.storage.ImageUploads;
import com.tattoostudio.util.Paging;
import com.tattoostudio.util.PhoneNumbers;

/**
 * Bookings from the customer's side: booking a flash design (held for 15 minutes while the deposit
 * is paid) or a free consultation (with reference photos), paying deposits, looking up, cancelling
 * and reviewing. Guests use code + phone; customers with an account also see their bookings in
 * My account.
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
    private final ImageUploads images;
    private final PaymentProvider payments;
    private final ReviewRepository reviews;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingRepository bookings, CatalogService catalog, ArtistRepository artists,
            AvailabilityService availability, BookingCodeGenerator codes, LookupAttemptLimiter limiter,
            BookingMailer mailer, BookingRules rules, Clock clock, MongoTemplate mongo, ImageUploads images,
            PaymentProvider payments, ReviewRepository reviews) {
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
        this.images = images;
        this.payments = payments;
        this.reviews = reviews;
    }

    // ---- Booking ----

    /**
     * Books a flash design. The booking holds the time and the design for 15 minutes (PENDING) while
     * the customer pays the deposit; then it is confirmed (BOOKED), or released if nobody paid.
     *
     * @param userId the signed-in customer account making the booking, or null for a guest
     */
    public BookingDto bookFlash(CreateFlashBookingRequest request, String userId) {
        expireHolds();
        FlashDesign flash = catalog.flash(request.flashId());
        Artist artist = catalog.activeArtist(flash.getArtistId());
        if (!flash.isAvailable() || catalog.isClaimed(flash.getId())) {
            throw flashTaken();
        }
        Instant start = requireBookable(request.date(), request.time());
        String phone = normalizePhone(request.phone());
        requireAdult(request.dateOfBirth(), request.date());
        Duration length = Duration.ofMinutes(flash.getDurationMinutes());

        Booking booking = new Booking();
        booking.setType(BookingType.FLASH);
        booking.setTitle(flash.getName());
        booking.setFlashId(flash.getId());
        booking.setWidthCm(flash.getWidthCm());
        booking.setHeightCm(flash.getHeightCm());
        booking.setPrice(flash.getPrice());
        BigDecimal deposit = rules.flashDeposit().min(flash.getPrice());
        if (deposit.signum() > 0) {
            booking.changeStatus(BookingStatus.PENDING);
            booking.setDepositAmount(deposit);
            booking.setDepositStatus(DepositStatus.WAITING);
            booking.setDepositDueAt(clock.instant().plus(rules.depositHold()));
            booking.setPaymentId(newPaymentId());
        } else {
            booking.changeStatus(BookingStatus.BOOKED);
        }
        booking.setNotes(blankToNull(request.notes()));
        fillCustomer(booking, request.customerName(), phone, request.email(), request.dateOfBirth(), userId);
        Booking saved = book(booking, artist, start, length, phone, userId);
        log.info("Flash {} ({}) held for {} at {}", saved.getCode(), flash.getName(), artist.getName(), start);
        BookingDto dto = toDto(saved, artist);
        if (saved.getStatus() == BookingStatus.BOOKED) {
            mailer.bookingConfirmed(dto, saved.getEmail());
        }
        return dto;
    }

    /** Books a free 30-minute consultation, with up to 5 reference photos. */
    public BookingDto bookConsultation(CreateConsultationRequest request, List<MultipartFile> photos, String userId) {
        expireHolds();
        Artist artist = catalog.activeArtist(request.artistId());
        Instant start = requireBookable(request.date(), request.time());
        String phone = normalizePhone(request.phone());
        requireAdult(request.dateOfBirth(), request.date());

        Booking booking = new Booking();
        booking.setType(BookingType.CONSULTATION);
        booking.changeStatus(BookingStatus.BOOKED);
        booking.setIdea(request.idea().trim());
        booking.setPlacement(request.placement());
        booking.setSizeCm(request.sizeCm());
        booking.setColour(request.colour());
        fillCustomer(booking, request.customerName(), phone, request.email(), request.dateOfBirth(), userId);
        // Check the time before uploading anything; book() checks again when saving
        if (!availability.isFree(artist, start, rules.consultationLength())) {
            throw slotUnavailable();
        }
        List<Image> stored = images.storeAll(photos, "references");
        booking.setPhotos(stored);
        Booking saved;
        try {
            saved = book(booking, artist, start, rules.consultationLength(), phone, userId);
        } catch (RuntimeException e) {
            images.deleteAll(stored);
            throw e;
        }
        log.info("Consultation {} booked with {} at {} ({} photos)", saved.getCode(), artist.getName(), start, stored.size());
        BookingDto dto = toDto(saved, artist);
        mailer.bookingConfirmed(dto, saved.getEmail());
        return dto;
    }

    private void fillCustomer(Booking booking, String name, String phone, String email, LocalDate dateOfBirth,
            String userId) {
        booking.setCustomerName(name.trim());
        booking.setPhone(phone);
        booking.setEmail(email.trim());
        booking.setDateOfBirth(dateOfBirth);
        booking.setConsentAt(clock.instant());
        booking.setUserId(userId);
    }

    /**
     * Checks the per-phone and per-account limits and that the time is free, then saves the booking.
     * One booking at a time per phone number and per account, so two simultaneous requests can't both
     * pass the limits (one server; the double-booking guard itself is in the database). Always phone
     * first, then account (separate lock sets), so two requests never wait on each other.
     */
    private Booking book(Booking booking, Artist artist, Instant start, Duration length, String phone, String userId) {
        synchronized (lockFor(phoneLocks, phone)) {
            if (userId == null) {
                return checkAndSave(booking, artist, start, length, phone, null);
            }
            synchronized (lockFor(accountLocks, userId)) {
                return checkAndSave(booking, artist, start, length, phone, userId);
            }
        }
    }

    private Booking checkAndSave(Booking booking, Artist artist, Instant start, Duration length, String phone,
            String userId) {
        Instant now = clock.instant();
        // Stops one person (or a script) from holding lots of times without an account
        if (bookings.countUpcomingByPhone(phone, now) >= rules.maxUpcomingPerPhone()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PHONE_LIMIT_REACHED, "This phone number already has "
                    + rules.maxUpcomingPerPhone() + " upcoming bookings. Please cancel one first or call the studio.");
        }
        // An account can't get around that by typing other phone numbers
        if (userId != null && bookings.countUpcomingByUser(userId, now) >= rules.maxUpcomingPerAccount()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.PHONE_LIMIT_REACHED, "Your account already has "
                    + rules.maxUpcomingPerAccount() + " upcoming bookings. Please cancel one first or call the studio.");
        }
        if (!availability.isFree(artist, start, length)) {
            throw slotUnavailable();
        }
        booking.setArtistId(artist.getId());
        booking.setStartsAt(start);
        booking.setEndsAt(start.plus(length));
        booking.setSlots(availability.slotsFor(start, start.plus(length), artist.getBreakMinutes()));
        booking.setCreatedAt(now);
        saveWithCode(booking);
        return booking;
    }

    /**
     * Saves a new booking with a fresh code. If the time or the flash design was taken a moment ago,
     * the database's unique indexes reject the save (see Booking). A clash on the booking code itself
     * (very unlikely) is retried with a new code.
     */
    void saveWithCode(Booking booking) {
        for (int attempt = 0; attempt < 5; attempt++) {
            booking.setCode(codes.next());
            try {
                bookings.insert(booking);
                return;
            } catch (DuplicateKeyException e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("no_double_booking")) {
                    throw slotUnavailable();
                }
                if (message.contains("one_booking_per_flash")) {
                    throw flashTaken();
                }
                // Otherwise the random code already existed: loop and try a new one
            }
        }
        throw new IllegalStateException("Could not generate a unique booking code");
    }

    /** The start as an instant, if customers may book it: from 2 hours to 30 days ahead. */
    private Instant requireBookable(LocalDate date, java.time.LocalTime time) {
        Instant start = availability.toInstant(date, time);
        if (start.isBefore(clock.instant().plus(rules.minNotice())) || !availability.isWithinBookingWindow(date)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OUTSIDE_BOOKING_WINDOW, "Bookings are possible from "
                    + rules.minNotice().toHours() + " hours ahead up to " + rules.maxDaysAhead() + " days ahead");
        }
        return start;
    }

    /** Customers must be 18 on the day of the appointment. */
    void requireAdult(LocalDate dateOfBirth, LocalDate appointment) {
        if (dateOfBirth.isBefore(LocalDate.of(1900, 1, 1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Please check your date of birth");
        }
        if (dateOfBirth.plusYears(rules.minAge()).isAfter(appointment)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UNDER_AGE,
                    "You need to be " + rules.minAge() + " or older to get tattooed.");
        }
    }

    static String normalizePhone(String phone) {
        try {
            return PhoneNumbers.normalize(phone);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }
    }

    String newPaymentId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // ---- Deposits ----

    /** Starts paying a waiting deposit (flash during its hold, or a session the artist reserved). */
    public PaymentStartDto startDeposit(Booking booking) {
        Instant now = clock.instant();
        if (booking.getDepositStatus() != DepositStatus.WAITING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOTHING_TO_PAY, booking.getDepositStatus() == DepositStatus.PAID
                    ? "The deposit is already paid." : "This booking has no deposit to pay.");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED || AvailabilityService.isLapsedHold(booking, now)) {
            expireHolds();
            throw holdExpired();
        }
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.BOOKED) {
            throw notActive(booking);
        }
        if (!booking.getStartsAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOTHING_TO_PAY, "The appointment has already started. Please pay in the studio.");
        }
        String paymentId = booking.getPaymentId();
        if (paymentId == null) {
            paymentId = newPaymentId();
            mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.getId()).and("paymentId").exists(false)),
                    new Update().set("paymentId", paymentId), Booking.class);
            paymentId = bookings.findById(booking.getId()).map(Booking::getPaymentId).orElse(paymentId);
        }
        Artist artist = artists.findById(booking.getArtistId()).orElse(null);
        String description = "Deposit for " + describe(booking) + (artist == null ? "" : " with " + artist.getName());
        return new PaymentStartDto(payments.checkoutUrl(paymentId, booking.getDepositAmount(), description),
                booking.getDepositAmount(), booking.getDepositDueAt());
    }

    /** What happened when a deposit payment came in. */
    public record DepositOutcome(String code, boolean confirmed) {
    }

    /**
     * The payment provider reports a deposit as paid: the booking is confirmed. If the hold had already
     * run out and someone else took the time or the design meanwhile, the money is refunded instead.
     * Reporting the same payment twice changes nothing.
     */
    public DepositOutcome depositPaid(String paymentId) {
        Booking booking = bookings.findByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Payment not found"));
        Instant now = clock.instant();
        if (booking.getDepositStatus() == DepositStatus.PAID) {
            if (booking.getDepositMethod() != DepositMethod.ONLINE) {
                refundSecondPayment(booking, paymentId);
            }
            // ONLINE: this payment was already recorded (the provider reported it twice)
            return new DepositOutcome(booking.getCode(), booking.getStatus() != BookingStatus.CANCELLED);
        }
        if (booking.getDepositStatus() == DepositStatus.REFUNDED) {
            return new DepositOutcome(booking.getCode(), false);
        }
        Update paid = confirmUpdate(booking).set("depositStatus", DepositStatus.PAID)
                .set("depositMethod", DepositMethod.ONLINE).set("depositPaidAt", now);
        Booking confirmed = mongo.findAndModify(Query.query(Criteria.where("_id").is(booking.getId())
                .and("depositStatus").is(DepositStatus.WAITING)
                .and("status").in(BookingStatus.PENDING, BookingStatus.BOOKED)),
                paid, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (confirmed == null && booking.getStatus() == BookingStatus.EXPIRED && booking.getStartsAt().isAfter(now)) {
            // Paid just after the hold ran out: take the time and design back if they are still free
            try {
                confirmed = mongo.findAndModify(Query.query(Criteria.where("_id").is(booking.getId())
                        .and("status").is(BookingStatus.EXPIRED)),
                        paid, FindAndModifyOptions.options().returnNew(true), Booking.class);
            } catch (DuplicateKeyException taken) {
                confirmed = null;
            }
        }
        if (confirmed == null) {
            Booking refunded = mongo.findAndModify(Query.query(Criteria.where("_id").is(booking.getId())
                    .and("depositStatus").nin(DepositStatus.PAID, DepositStatus.REFUNDED)),
                    new Update().set("depositStatus", DepositStatus.REFUNDED).set("depositMethod", DepositMethod.ONLINE)
                            .set("depositPaidAt", now).set("depositRefundedAt", now),
                    FindAndModifyOptions.options().returnNew(true), Booking.class);
            if (refunded != null) {
                payments.refund(paymentId, refunded.getDepositAmount());
                log.info("Deposit for {} came in after the booking was released; refunded", booking.getCode());
                return new DepositOutcome(booking.getCode(), false);
            }
            // Paid in the studio a moment ago (the admin marked it): this online payment is a second one
            Booking current = bookings.findById(booking.getId()).orElse(booking);
            if (current.getDepositStatus() == DepositStatus.PAID && current.getDepositMethod() != DepositMethod.ONLINE) {
                refundSecondPayment(current, paymentId);
                return new DepositOutcome(current.getCode(), current.getStatus() != BookingStatus.CANCELLED);
            }
            return new DepositOutcome(booking.getCode(), false);
        }
        log.info("Deposit paid for {}", confirmed.getCode());
        mailer.bookingConfirmed(toDto(confirmed, artists.findById(confirmed.getArtistId()).orElse(null)),
                confirmed.getEmail());
        return new DepositOutcome(confirmed.getCode(), true);
    }

    /**
     * The deposit was already paid another way (in cash) when this online payment came in: give the
     * online one back. The payment id is removed first, so a repeated report never refunds twice.
     */
    private void refundSecondPayment(Booking booking, String paymentId) {
        boolean claimed = mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.getId()).and("paymentId").is(paymentId)),
                new Update().unset("paymentId"), Booking.class).getModifiedCount() == 1;
        if (claimed) {
            payments.refund(paymentId, booking.getDepositAmount());
            log.info("Deposit for {} was already paid in cash; refunded the online payment", booking.getCode());
        }
    }

    /** Status BOOKED with its index flags (the deposit fields are added by the caller). */
    private static Update confirmUpdate(Booking booking) {
        return new Update().set("status", BookingStatus.BOOKED).set("holdsTime", true)
                .set("claimsFlash", booking.getType() == BookingType.FLASH);
    }

    /**
     * Releases flash bookings whose 15 minutes to pay ran out: their time and design are free again.
     * Runs every minute (HoldExpiryJob) and before every new booking.
     */
    public long expireHolds() {
        Query lapsed = Query.query(Criteria.where("status").is(BookingStatus.PENDING).and("depositDueAt").lte(clock.instant()));
        Update update = new Update().set("status", BookingStatus.EXPIRED).set("holdsTime", false).set("claimsFlash", false)
                .set("depositStatus", DepositStatus.NOT_PAID);
        return mongo.updateMulti(lapsed, update, Booking.class).getModifiedCount();
    }

    // ---- Finding bookings ----

    /**
     * Finds the booking for a code + phone pair. Wrong code and wrong phone give the same answer,
     * so the response never reveals whether a code exists.
     */
    public Booking findForCustomer(String code, String phone, String clientKey) {
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
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND,
                    "No booking matches this code and phone number. Check both and try again.");
        }
        return booking;
    }

    /** One of the signed-in customer's own bookings. */
    public Booking findForAccount(String code, String userId) {
        return bookings.findByCode(BookingCodeGenerator.normalize(code))
                .filter(b -> userId != null && userId.equals(b.getUserId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.BOOKING_NOT_FOUND,
                        "This booking isn't in your account"));
    }

    public BookingDto toDto(Booking booking) {
        return toDto(booking, artists.findById(booking.getArtistId()).orElse(null));
    }

    /** Which of a customer's bookings to show: still to come, already over (or cancelled), or all. */
    public enum When { UPCOMING, PAST, ALL }

    static final Map<String, String> ACCOUNT_SORTS = Map.of("date", "startsAt", "title", "title", "type", "type",
            "price", "price", "status", "status");

    /**
     * The bookings of this customer account. The search text matches the title, the idea and the
     * booking code. Upcoming = held or booked and not over yet, earliest first; otherwise latest first.
     */
    public PageDto<BookingDto> forAccount(String userId, When when, BookingType type, BookingStatus status, ListQuery list) {
        Instant now = clock.instant();
        List<Criteria> filters = Paging.filters();
        filters.add(Criteria.where("userId").is(userId));
        if (when == When.UPCOMING) {
            filters.add(Criteria.where("status").in(BookingStatus.PENDING, BookingStatus.BOOKED).and("endsAt").gt(now));
        } else if (when == When.PAST) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("status").nin(BookingStatus.PENDING, BookingStatus.BOOKED),
                    Criteria.where("endsAt").lte(now)));
        }
        if (type != null) {
            filters.add(Criteria.where("type").is(type));
        }
        if (status != null) {
            filters.add(Criteria.where("status").is(status));
        }
        if (list.text() != null) {
            filters.add(Paging.contains(list.text(), "title", "idea", "code"));
        }
        return Paging.find(mongo, Booking.class, filters, list, ACCOUNT_SORTS,
                when == When.UPCOMING ? "date,asc" : "date,desc", page -> {
                    Map<String, Artist> byId = artists.findAllById(page.stream().map(Booking::getArtistId).distinct().toList())
                            .stream().collect(Collectors.toMap(Artist::getId, Function.identity()));
                    return page.stream().map(b -> toDto(b, byId.get(b.getArtistId()))).toList();
                });
    }

    // ---- Cancelling ----

    /**
     * Cancels a booking. Customers can cancel online until the appointment starts: a paid deposit is
     * refunded if that is at least 48 hours ahead, otherwise the studio keeps it. When the studio
     * cancels ({@code byStudio}), a paid deposit is always refunded, at any time.
     */
    public Booking cancel(Booking booking, boolean byStudio) {
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.BOOKED) {
            throw notActive(booking);
        }
        if (!byStudio && !now.isBefore(booking.getStartsAt())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CANCELLATION_CLOSED,
                    "The appointment has already started. Please call the studio.");
        }
        DepositStatus deposit = booking.getDepositStatus();
        DepositStatus after = switch (deposit) {
            case WAITING -> DepositStatus.NOT_PAID;
            case PAID -> byStudio || !now.isAfter(refundDeadline(booking)) ? DepositStatus.REFUNDED : DepositStatus.KEPT;
            default -> deposit;
        };
        Update update = new Update().set("status", BookingStatus.CANCELLED).set("holdsTime", false)
                .set("claimsFlash", false).set("cancelledAt", now).set("depositStatus", after);
        if (after == DepositStatus.REFUNDED) {
            update.set("depositRefundedAt", now);
        }
        Booking saved = mongo.findAndModify(Query.query(Criteria.where("_id").is(booking.getId())
                .and("status").is(booking.getStatus()).and("depositStatus").is(deposit)),
                update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (saved == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "This booking was just changed. Please look it up again.");
        }
        if (after == DepositStatus.REFUNDED && saved.getDepositMethod() == DepositMethod.ONLINE) {
            payments.refund(saved.getPaymentId(), saved.getDepositAmount());
        }
        log.info("Cancelled {}{} (deposit {})", saved.getCode(), byStudio ? " by the studio" : "", after);
        if (saved.getEmail() != null && saved.getStartsAt().isAfter(now)) {
            mailer.bookingCancelled(toDto(saved), saved.getEmail(), byStudio);
        }
        return saved;
    }

    /**
     * Sets a booking's status (with its index flags) if it still has the {@code expected} one, and
     * returns the updated booking (empty if it changed meanwhile). Only these fields are written, so a
     * change made at the same moment (e.g. the privacy job wiping customer details) is never undone.
     */
    public Optional<Booking> changeStatus(Booking booking, BookingStatus newStatus) {
        Update update = new Update().set("status", newStatus).set("holdsTime", newStatus.holdsTime())
                .set("claimsFlash", booking.getType() == BookingType.FLASH && newStatus.claimsFlash());
        try {
            return Optional.ofNullable(mongo.findAndModify(
                    Query.query(Criteria.where("_id").is(booking.getId()).and("status").is(booking.getStatus())),
                    update, FindAndModifyOptions.options().returnNew(true), Booking.class));
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "The flash design or the time has been booked again meanwhile, so this can't be changed back");
        }
    }

    // ---- Reviews ----

    /** Reviews a finished flash design or session (once). It shows on the site after the admin approves it. */
    public void review(Booking booking, CreateReviewRequest request) {
        if (booking.getType() == BookingType.CONSULTATION || booking.getStatus() != BookingStatus.DONE
                || booking.isAnonymized()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REVIEW_NOT_ALLOWED,
                    "Reviews are for finished tattoo appointments.");
        }
        boolean claimed = mongo.updateFirst(Query.query(Criteria.where("_id").is(booking.getId()).and("reviewedAt").is(null)),
                new Update().set("reviewedAt", clock.instant()), Booking.class).getModifiedCount() == 1;
        if (!claimed) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.REVIEW_NOT_ALLOWED, "You already reviewed this appointment.");
        }
        Review review = new Review(booking.getArtistId(), firstName(booking.getCustomerName()), request.rating(),
                request.text().trim(), false, clock.instant());
        review.setBookingId(booking.getId());
        reviews.save(review);
        log.info("New review for {} ({} stars)", booking.getCode(), request.rating());
    }

    static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Client";
        }
        return name.trim().split("\\s+")[0];
    }

    // ---- Helpers ----

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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** "Small dragon", "Back piece, session 1 of 3" or "a consultation". */
    static String describe(Booking b) {
        return b.getTitle() != null ? b.getTitle() : "a consultation";
    }

    public Instant refundDeadline(Booking b) {
        return b.getStartsAt().minus(rules.refundCutoff());
    }

    private ApiException notActive(Booking booking) {
        String status = switch (booking.getStatus()) {
            case CANCELLED -> "cancelled";
            case EXPIRED -> "released because the deposit wasn't paid in time";
            case DONE -> "finished";
            case NO_SHOW -> "marked as missed";
            default -> booking.getStatus().name().toLowerCase();
        };
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BOOKING_NOT_ACTIVE, "This booking is " + status + ".");
    }

    private static ApiException slotUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.SLOT_UNAVAILABLE, "Sorry, that time was just taken. Please pick another time.");
    }

    private static ApiException flashTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.FLASH_NOT_AVAILABLE,
                "Sorry, this design was just booked by someone else. Every flash design is tattooed once.");
    }

    private static ApiException holdExpired() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.HOLD_EXPIRED,
                "The hold expired and the time is free again. Pick a time again to continue.");
    }

    BookingDto toDto(Booking b, Artist artist) {
        Instant now = clock.instant();
        ZonedDateTime start = b.getStartsAt().atZone(rules.timezone());
        ZonedDateTime end = b.getEndsAt().atZone(rules.timezone());
        boolean active = b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.BOOKED;
        Instant refundDeadline = b.getDepositAmount() == null ? null : refundDeadline(b);
        boolean canPay = b.getDepositStatus() == DepositStatus.WAITING && active && b.getStartsAt().isAfter(now)
                && !AvailabilityService.isLapsedHold(b, now);
        BigDecimal leftToPay = b.getPrice() == null ? null
                : b.getDepositAmount() == null ? b.getPrice() : b.getPrice().subtract(b.getDepositAmount()).max(BigDecimal.ZERO);
        return new BookingDto(
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
                leftToPay,
                b.getCustomerName(),
                PhoneNumbers.mask(b.getPhone()),
                b.getType() == BookingType.CONSULTATION ? ConsultationDto.from(b) : null,
                active && b.getStartsAt().isAfter(now),
                refundDeadline,
                b.getDepositStatus() == DepositStatus.PAID && refundDeadline != null && !now.isAfter(refundDeadline),
                canPay,
                b.getType() != BookingType.CONSULTATION && b.getStatus() == BookingStatus.DONE && b.getReviewedAt() == null
                        && !b.isAnonymized());
    }
}
