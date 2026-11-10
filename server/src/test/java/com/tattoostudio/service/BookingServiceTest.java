package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.CreateConsultationRequest;
import com.tattoostudio.dto.CreateFlashBookingRequest;
import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.PaymentStartDto;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.ColourChoice;
import com.tattoostudio.model.DepositMethod;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Placement;
import com.tattoostudio.storage.ImageSettings;
import com.tattoostudio.support.ApiTest;

class BookingServiceTest extends ApiTest {

    @Autowired
    BookingService bookingService;
    @Autowired
    LookupAttemptLimiter limiter;
    @Autowired
    AnonymizationJob anonymization;
    @Autowired
    ImageSettings imageSettings;
    @Autowired
    StaffBookingService staffBookings;

    // --- Flash ---

    @Test
    void aFlashBookingHoldsTheTimeAndDesignForFifteenMinutes() {
        BookingDto b = bookFlash("Small dragon", "14:00", "0888123456");
        assertThat(b.code()).matches("[A-Z2-9]{4}-[A-Z2-9]{2}");
        assertThat(b.type()).isEqualTo(BookingType.FLASH);
        assertThat(b.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.title()).isEqualTo("Small dragon");
        assertThat(b.endTime()).isEqualTo(LocalTime.of(16, 0)); // about 2 hours
        assertThat(b.price()).isEqualByComparingTo("180");
        assertThat(b.deposit().amount()).isEqualByComparingTo("30");
        assertThat(b.deposit().status()).isEqualTo(DepositStatus.WAITING);
        assertThat(b.deposit().dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(b.leftToPay()).isEqualByComparingTo("150");
        assertThat(b.canPayDeposit()).isTrue();
        assertThat(code(() -> bookFlash("Small dragon", "18:00", "0888999999"))).isEqualTo("FLASH_NOT_AVAILABLE");
    }

    @Test
    void theDatabaseLetsOnlyOneBookingClaimADesign() {
        BookingDto first = bookFlash("Small dragon", "14:00", "0888123456");
        Booking copy = bookings.findByCode(first.code()).orElseThrow();
        copy.setId(null);
        copy.setCode("COPY-01");
        copy.setStartsAt(at(TUESDAY.plusDays(1), "14:00")); // another day, so only the design clashes
        copy.setSlots(List.of(at(TUESDAY.plusDays(1), "14:00")));
        assertThatThrownBy(() -> bookings.insert(copy)).isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("one_booking_per_flash");
    }

    @Test
    void theDatabaseRejectsOverlappingBookings() {
        booking("Kai", TUESDAY, "14:00", BookingStatus.BOOKED);
        assertThatThrownBy(() -> booking("Kai", TUESDAY, "14:30", BookingStatus.BOOKED))
                .isInstanceOf(DuplicateKeyException.class).hasMessageContaining("no_double_booking");
    }

    @Test
    void timesOutsideTheWindowOrNotFreeAreRefused() {
        assertThat(code(() -> bookFlash("Small dragon", MONDAY, "09:00", "0888123456"))).isEqualTo("OUTSIDE_BOOKING_WINDOW");
        assertThat(code(() -> bookFlash("Small dragon", TUESDAY.plusDays(31), "14:00", "0888123456")))
                .isEqualTo("OUTSIDE_BOOKING_WINDOW");
        assertThat(code(() -> bookFlash("Small dragon", "22:00", "0888123456"))).isEqualTo("SLOT_UNAVAILABLE"); // past closing
        assertThat(code(() -> bookFlash("Small dragon", "14:15", "0888123456"))).isEqualTo("SLOT_UNAVAILABLE"); // off the grid
        booking("Kai", TUESDAY, "15:00", BookingStatus.BOOKED);
        assertThat(code(() -> bookFlash("Small dragon", "14:00", "0888123456"))).isEqualTo("SLOT_UNAVAILABLE");
    }

    @Test
    void customersMustBeEighteenOnTheDay() {
        LocalDate eighteenOnWednesday = TUESDAY.plusDays(1).minusYears(18);
        assertThat(code(() -> bookingService.bookFlash(flashRequest("Small dragon", TUESDAY, "14:00", "0888123456",
                eighteenOnWednesday), null))).isEqualTo("UNDER_AGE");
        BookingDto onTheBirthday = bookingService.bookFlash(flashRequest("Small dragon", TUESDAY.plusDays(1), "14:00",
                "0888123456", eighteenOnWednesday), null);
        assertThat(onTheBirthday.status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void invalidPhoneNumberIsRefused() {
        assertThat(code(() -> bookFlash("Small dragon", "14:00", "12"))).isEqualTo("INVALID_PHONE");
    }

    @Test
    void hiddenDesignsCantBeBooked() {
        var dragon = flash("Small dragon");
        dragon.setAvailable(false);
        flash.save(dragon);
        assertThat(code(() -> bookFlash("Small dragon", "14:00", "0888123456"))).isEqualTo("FLASH_NOT_AVAILABLE");
    }

    // --- Deposits ---

    @Test
    void payingTheDepositConfirmsTheBooking() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        PaymentStartDto payment = bookingService.startDeposit(find(held));
        assertThat(payment.checkoutUrl()).startsWith("/api/payments/demo/");
        assertThat(payment.amount()).isEqualByComparingTo("30");

        BookingService.DepositOutcome outcome = bookingService.depositPaid(find(held).getPaymentId());
        assertThat(outcome.confirmed()).isTrue();
        Booking paid = find(held);
        assertThat(paid.getStatus()).isEqualTo(BookingStatus.BOOKED);
        assertThat(paid.getDepositStatus()).isEqualTo(DepositStatus.PAID);
        assertThat(paid.getDepositMethod()).isEqualTo(DepositMethod.ONLINE);
        // Reporting the same payment again changes nothing
        assertThat(bookingService.depositPaid(paid.getPaymentId()).confirmed()).isTrue();
        assertThat(code(() -> bookingService.startDeposit(find(held)))).isEqualTo("NOTHING_TO_PAY");
    }

    @Test
    void unpaidHoldsAreReleasedAfterFifteenMinutes() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        clock.advance(Duration.ofMinutes(16));
        assertThat(code(() -> bookingService.startDeposit(find(held)))).isEqualTo("HOLD_EXPIRED");
        assertThat(find(held).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(find(held).getDepositStatus()).isEqualTo(DepositStatus.NOT_PAID);
        // The time and the design are free again
        assertThat(bookFlash("Small dragon", "14:00", "0888999999").status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void aLatePaymentTakesTheBookingBackIfItIsStillFree() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        clock.advance(Duration.ofMinutes(16));
        bookingService.expireHolds();
        assertThat(bookingService.depositPaid(find(held).getPaymentId()).confirmed()).isTrue();
        assertThat(find(held).getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void aLatePaymentIsRefundedIfSomeoneElseTookTheDesign() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        clock.advance(Duration.ofMinutes(16));
        bookFlash("Small dragon", "18:00", "0888999999"); // releases the old hold and takes the design
        assertThat(bookingService.depositPaid(find(held).getPaymentId()).confirmed()).isFalse();
        Booking late = find(held);
        assertThat(late.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(late.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
    }

    @Test
    void anOnlinePaymentAfterACashPaymentIsRefundedOnce() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        String paymentId = find(held).getPaymentId();
        staffBookings.depositPaidInCash(find(held).getId()); // the admin, a moment before the online payment
        assertThat(bookingService.depositPaid(paymentId).confirmed()).isTrue();
        Booking b = find(held);
        assertThat(b.getDepositMethod()).isEqualTo(DepositMethod.CASH);
        assertThat(b.getPaymentId()).isNull(); // refunded, and a repeated report finds nothing to refund again
        assertThat(code(() -> bookingService.depositPaid(paymentId))).isEqualTo("NOT_FOUND");
    }

    // --- Cancelling ---

    @Test
    void cancellingFortyEightHoursAheadRefundsTheDeposit() {
        Booking b = paidFlash("Small dragon", TUESDAY.plusDays(3), "14:00");
        Booking cancelled = bookingService.cancel(b, false);
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
        assertThat(cancelled.isHoldsTime()).isFalse();
        assertThat(cancelled.isClaimsFlash()).isFalse();
        assertThat(bookingService.bookFlash(flashRequest("Small dragon", TUESDAY.plusDays(3), "14:00", "0888999999",
                LocalDate.of(1990, 1, 1)), null).status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void cancellingLaterKeepsTheDeposit() {
        Booking b = paidFlash("Small dragon", TUESDAY, "14:00"); // 30 hours ahead
        assertThat(bookingService.toDto(b).refundIfCancelledNow()).isFalse();
        assertThat(bookingService.cancel(b, false).getDepositStatus()).isEqualTo(DepositStatus.KEPT);
    }

    @Test
    void theStudioCancellingAlwaysRefunds() {
        Booking b = paidFlash("Small dragon", TUESDAY, "14:00");
        assertThat(bookingService.cancel(b, true).getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
    }

    @Test
    void cancellingAHoldDropsTheWaitingDeposit() {
        BookingDto held = bookFlash("Small dragon", "14:00", "0888123456");
        Booking cancelled = bookingService.cancel(find(held), false);
        assertThat(cancelled.getDepositStatus()).isEqualTo(DepositStatus.NOT_PAID);
    }

    @Test
    void onlineCancellationClosesWhenTheAppointmentStarts() {
        Booking b = paidFlash("Small dragon", TUESDAY, "14:00");
        clock.set(at(TUESDAY, "14:00"));
        assertThat(code(() -> bookingService.cancel(find(b.getCode()), false))).isEqualTo("CANCELLATION_CLOSED");
        assertThat(code(() -> bookingService.cancel(bookingService.cancel(find(b.getCode()), true), false)))
                .isEqualTo("BOOKING_NOT_ACTIVE");
    }

    // --- Consultations ---

    @Test
    void aConsultationIsBookedAtOnceWithItsPhotos() throws Exception {
        BookingDto b = bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888123456"),
                List.of(png("koi.png"), png("maple.png")), null);
        assertThat(b.type()).isEqualTo(BookingType.CONSULTATION);
        assertThat(b.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(b.deposit().status()).isEqualTo(DepositStatus.NONE);
        assertThat(b.endTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(b.consultation().placement()).isEqualTo(Placement.FOREARM);
        assertThat(b.consultation().photos()).hasSize(2);
        for (Image photo : b.consultation().photos()) {
            assertThat(Files.exists(uploads().resolve(photo.id()))).isTrue();
        }
    }

    @Test
    void photosAreDeletedAgainWhenTheBookingFails() throws Exception {
        long before = countUploads();
        booking("Nika", TUESDAY, "12:30", BookingStatus.BOOKED);
        assertThat(code(() -> bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888123456"),
                List.of(png("koi.png")), null))).isEqualTo("SLOT_UNAVAILABLE");
        assertThat(countUploads()).isEqualTo(before);
    }

    @Test
    void onlyPicturesAreAccepted() {
        MultipartFile text = new MockMultipartFile("photos", "notes.txt", "image/png", "hello".getBytes());
        assertThat(code(() -> bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888123456"),
                List.of(text), null))).isEqualTo("INVALID_FILE");
        List<MultipartFile> six = List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"), png("5.png"), png("6.png"));
        assertThat(code(() -> bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888123456"),
                six, null))).isEqualTo("INVALID_FILE");
    }

    // --- Lookup ---

    @Test
    void lookupAcceptsAnyPhoneAndCodeFormat() {
        BookingDto b = bookFlash("Small dragon", "14:00", "0888 123 456");
        String typed = " " + b.code().toLowerCase().replace("-", "") + " ";
        assertThat(bookingService.findForCustomer(typed, "+359 888 123 456", "lookup-format").getCode()).isEqualTo(b.code());
    }

    @Test
    void tooManyWrongAttemptsAreBlockedForFifteenMinutes() {
        BookingDto b = bookFlash("Small dragon", "14:00", "0888123456");
        String ip = "rate-limit-test";
        for (int i = 0; i < 5; i++) {
            assertThat(code(() -> bookingService.findForCustomer(b.code(), "0888000000", ip))).isEqualTo("BOOKING_NOT_FOUND");
        }
        assertThat(code(() -> bookingService.findForCustomer(b.code(), "0888123456", ip))).isEqualTo("TOO_MANY_ATTEMPTS");
        clock.advance(Duration.ofMinutes(16));
        assertThat(bookingService.findForCustomer(b.code(), "0888123456", ip).getCode()).isEqualTo(b.code());
    }

    @Test
    void expiredFailuresAreForgotten() {
        limiter.recordFailure("purge-test");
        assertThat(limiter.isTracking("purge-test")).isTrue();
        clock.advance(Duration.ofMinutes(16));
        limiter.purgeExpired();
        assertThat(limiter.isTracking("purge-test")).isFalse();
    }

    // --- Limits ---

    @Test
    void onePhoneCanHoldAtMostTwoUpcomingBookings() {
        bookFlash("Small dragon", "14:00", "0888777666");
        bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888777666"), List.of(), null);
        assertThat(code(() -> bookFlash("Peony", "18:00", "0888777666"))).isEqualTo("PHONE_LIMIT_REACHED");
    }

    @Test
    void sessionsArtistsReserveDontCountTowardsTheLimit() {
        Booking session = booking("Dario", TUESDAY.plusDays(2), "11:00", BookingStatus.BOOKED);
        session.setPhone("+359888777666");
        bookings.save(session);
        bookFlash("Small dragon", "14:00", "0888777666");
        assertThat(bookFlash("Peony", "18:00", "0888777666").status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void phoneLimitHoldsForSimultaneousBookings() throws Exception {
        List<String> outcomes = simultaneously(List.of(
                () -> bookFlash("Small dragon", "14:00", "0888 777 666"),
                () -> bookFlash("Peony", "14:00", "0888 777 666"),
                () -> bookFlash("Oni", "14:00", "0888 777 666")));
        assertThat(outcomes).containsExactlyInAnyOrder("booked", "booked", "PHONE_LIMIT_REACHED");
    }

    @Test
    void oneDesignGoesToOneOfManySimultaneousCustomers() throws Exception {
        List<String> outcomes = simultaneously(List.of(
                () -> bookFlash("Small dragon", "14:00", "0888 700 001"),
                () -> bookFlash("Small dragon", "18:00", "0888 700 002"),
                () -> bookFlash("Small dragon", "20:30", "0888 700 003")));
        assertThat(outcomes).containsOnlyOnce("booked");
        assertThat(outcomes.stream().filter(o -> !o.equals("booked"))).allMatch(o -> o.equals("FLASH_NOT_AVAILABLE"));
    }

    @Test
    void accountLimitHoldsForSimultaneousBookingsWithDifferentPhones() throws Exception {
        List<String> outcomes = simultaneously(List.of(
                () -> bookingService.bookFlash(flashRequest("Small dragon", TUESDAY, "14:00", "0888 700 001", null), "test-account"),
                () -> bookingService.bookFlash(flashRequest("Peony", TUESDAY, "14:00", "0888 700 002", null), "test-account"),
                () -> bookingService.bookFlash(flashRequest("Oni", TUESDAY, "14:00", "0888 700 003", null), "test-account")));
        assertThat(outcomes).containsExactlyInAnyOrder("booked", "booked", "PHONE_LIMIT_REACHED");
    }

    // --- Reviews ---

    @Test
    void finishedAppointmentsCanBeReviewedOnce() {
        Booking done = booking("Kai", MONDAY.minusDays(1), "14:00", BookingStatus.DONE);
        assertThat(bookingService.toDto(done).canReview()).isTrue();
        bookingService.review(done, new CreateReviewRequest(5, "The colour healed bright and solid."));
        var review = reviews.findAll().stream().filter(r -> done.getId().equals(r.getBookingId())).findFirst().orElseThrow();
        assertThat(review.getName()).isEqualTo("Ivan");
        assertThat(review.getArtistId()).isEqualTo(artist("Kai").getId());
        assertThat(review.isApproved()).isFalse();
        assertThat(code(() -> bookingService.review(find(done.getCode()), new CreateReviewRequest(4, "Again"))))
                .isEqualTo("REVIEW_NOT_ALLOWED");
    }

    @Test
    void upcomingAppointmentsAndConsultationsCantBeReviewed() {
        Booking upcoming = booking("Kai", TUESDAY, "14:00", BookingStatus.BOOKED);
        Booking consultation = booking("Kai", MONDAY.minusDays(1), "14:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        assertThat(code(() -> bookingService.review(upcoming, new CreateReviewRequest(5, "Great")))).isEqualTo("REVIEW_NOT_ALLOWED");
        assertThat(code(() -> bookingService.review(consultation, new CreateReviewRequest(5, "Great")))).isEqualTo("REVIEW_NOT_ALLOWED");
    }

    // --- Privacy ---

    @Test
    void customerDetailsAndPhotosAreWipedHalfAYearAfterTheAppointment() throws Exception {
        BookingDto dto = bookingService.bookConsultation(consultationRequest("Nika", "12:30", "0888123456"),
                List.of(png("koi.png")), null);
        Image photo = dto.consultation().photos().get(0);
        clock.set(at(TUESDAY, "13:00").plus(Duration.ofDays(179)));
        anonymization.run();
        assertThat(find(dto.code()).isAnonymized()).isFalse();

        clock.set(at(TUESDAY, "13:00").plus(Duration.ofDays(181)));
        anonymization.run();
        Booking wiped = find(dto.code());
        assertThat(wiped.isAnonymized()).isTrue();
        assertThat(wiped.getCustomerName()).isNull();
        assertThat(wiped.getPhone()).isNull();
        assertThat(wiped.getDateOfBirth()).isNull();
        assertThat(wiped.getIdea()).isNull();
        assertThat(wiped.getPhotos()).isEmpty();
        assertThat(Files.exists(uploads().resolve(photo.id()))).isFalse();
        assertThat(wiped.getArtistId()).isEqualTo(artist("Nika").getId()); // the booking itself stays
    }

    // --- Helpers ---

    private BookingDto bookFlash(String design, String time, String phone) {
        return bookFlash(design, TUESDAY, time, phone);
    }

    private BookingDto bookFlash(String design, LocalDate date, String time, String phone) {
        return bookingService.bookFlash(flashRequest(design, date, time, phone, null), null);
    }

    private CreateFlashBookingRequest flashRequest(String design, LocalDate date, String time, String phone,
            LocalDate dateOfBirth) {
        return new CreateFlashBookingRequest(flash(design).getId(), date, LocalTime.parse(time), "Test Customer", phone,
                "test@example.com", dateOfBirth == null ? LocalDate.of(1990, 1, 1) : dateOfBirth, null, true, true, true);
    }

    private CreateConsultationRequest consultationRequest(String artistName, String time, String phone) {
        return new CreateConsultationRequest(artist(artistName).getId(), TUESDAY, LocalTime.parse(time), "Test Customer",
                phone, "test@example.com", LocalDate.of(1990, 1, 1), "A crane over waves", Placement.FOREARM, 18,
                ColourChoice.BLACK_AND_GREY, true, true);
    }

    /** A flash design booked and paid online. */
    private Booking paidFlash(String design, LocalDate date, String time) {
        BookingDto held = bookFlash(design, date, time, "0888123456");
        bookingService.depositPaid(find(held).getPaymentId());
        return find(held);
    }

    private Booking find(BookingDto dto) {
        return find(dto.code());
    }

    private Booking find(String code) {
        return bookings.findByCode(code).orElseThrow();
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("photos", name, "image/png", PNG);
    }

    private Path uploads() {
        return imageSettings.localDir().toAbsolutePath().normalize();
    }

    private long countUploads() throws Exception {
        Path dir = uploads();
        if (!Files.exists(dir)) {
            return 0;
        }
        try (var files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    /** The error code a call fails with (or "ok"). */
    private static String code(Runnable action) {
        try {
            action.run();
            return "ok";
        } catch (ResponseStatusException e) {
            return String.valueOf(e.getBody().getProperties().get("code"));
        }
    }

    /** Starts these bookings at the same moment; returns "booked" or the error code for each. */
    private static List<String> simultaneously(List<Callable<BookingDto>> attempts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (Callable<BookingDto> attempt : attempts) {
            results.add(pool.submit(() -> {
                go.await();
                try {
                    attempt.call();
                    return "booked";
                } catch (ResponseStatusException e) {
                    return String.valueOf(e.getBody().getProperties().get("code"));
                }
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return outcomes;
    }

    @SuppressWarnings("unused")
    private static BigDecimal euros(int amount) {
        return BigDecimal.valueOf(amount);
    }
}
