package com.tattoostudio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.support.IntegrationTest;

/** Booking rules. Test day is a Tuesday: Melo, Sol, Nia, Cruz and Rook work 10:00–20:00, Jax is off. */
class BookingServiceTest extends IntegrationTest {

    @Autowired
    BookingService bookingService;
    @Autowired
    AvailabilityService availability;
    @Autowired
    AnonymizationJob anonymizationJob;
    @Autowired
    LookupAttemptLimiter limiter;

    // --- Creating bookings ---

    @Test
    void createsOneHourBookingWithCodePriceAndNotes() {
        BookingDto booking = book("Skin Fade", "Nia", "10:00", "0888 123 456");

        assertThat(booking.code()).matches("FS-[A-HJKMNP-Z2-9]{6}");
        assertThat(booking.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(booking.artistName()).isEqualTo("Nia Banks");
        assertThat(booking.price()).isEqualByComparingTo("34.00");
        assertThat(Duration.between(booking.startsAt(), booking.endsAt())).isEqualTo(Duration.ofHours(1));
        assertThat(booking.notes()).isEqualTo("Keep some length on top.");
        assertThat(booking.phoneMasked()).isEqualTo("+359******456");
        assertThat(bookings.findByCode(booking.code()).orElseThrow().getPhone()).isEqualTo("+359888123456");
    }

    @Test
    void sameTimeCannotBeBookedTwice() {
        book("Street Cut", "Nia", "10:00", "0888111111");
        assertStatus(() -> book("Street Cut", "Nia", "10:00", "0888222222"), HttpStatus.CONFLICT);
    }

    @Test
    void databaseItselfRejectsDoubleBookings() {
        // Bypasses all checks in BookingService: the unique slot index must still stop it
        bookings.save(rawBooking("FS-AAAAAA", at(TUESDAY, "10:00")));
        assertThatThrownBy(() -> bookings.save(rawBooking("FS-BBBBBB", at(TUESDAY, "10:00"))))
                .isInstanceOf(DuplicateKeyException.class);

        // A cancelled booking no longer holds its slot
        Booking first = bookings.findByCode("FS-AAAAAA").orElseThrow();
        first.setStatus(BookingStatus.CANCELLED);
        bookings.save(first);
        bookings.save(rawBooking("FS-BBBBBB", at(TUESDAY, "10:00")));
    }

    @Test
    void rejectsTimesOutsideTheBookingWindow() {
        clock.set(at(TUESDAY, "09:30"));
        assertStatus(() -> book("Street Cut", "Nia", "10:00", "0888123456"), HttpStatus.BAD_REQUEST); // < 1 h ahead
        assertStatus(() -> bookingService.create(new CreateBookingRequest(service("Street Cut").getId(),
                artist("Nia").getId(), TUESDAY.plusDays(35), LocalTime.of(10, 0), "A", "0888123456", null, null)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsTimesThatAreNotFreeSlots() {
        assertStatus(() -> book("Street Cut", "Nia", "20:00", "0888123456"), HttpStatus.CONFLICT); // closing time
        assertStatus(() -> book("Street Cut", "Nia", "10:30", "0888123456"), HttpStatus.CONFLICT); // not a slot start
        assertStatus(() -> book("Street Cut", "Jax", "10:00", "0888123456"), HttpStatus.CONFLICT); // day off
    }

    @Test
    void rejectsInvalidPhoneNumber() {
        assertStatus(() -> book("Street Cut", "Nia", "10:00", "call me"), HttpStatus.BAD_REQUEST);
    }

    @Test
    void anyArtistGoesToTheLeastBusyFreeArtist() {
        book("Street Cut", "Melo", "12:00", "0888000001");
        book("Street Cut", "Melo", "14:00", "0888000002");
        book("Street Cut", "Sol", "12:00", "0888000003");
        book("Street Cut", "Nia", "12:00", "0888000004");
        book("Street Cut", "Cruz", "12:00", "0888000005");

        BookingDto any = book("Street Cut", null, "10:00", "0888000006");
        assertThat(any.artistName()).isEqualTo("Rook Davis"); // free at 10:00 with no bookings yet
    }

    @Test
    void anyArtistFailsWhenEveryoneIsBusy() {
        List<String> working = List.of("Melo", "Sol", "Nia", "Cruz", "Rook");
        for (int i = 0; i < working.size(); i++) {
            book("Street Cut", working.get(i), "10:00", "088800000" + i);
        }
        assertStatus(() -> book("Street Cut", null, "10:00", "0888000009"), HttpStatus.CONFLICT);
    }

    // --- Looking up and cancelling ---

    @Test
    void lookupAcceptsAnyPhoneAndCodeFormat() {
        BookingDto booking = book("Street Cut", "Nia", "10:00", "0888 123 456");
        BookingDto found = bookingService.lookup(booking.code().toLowerCase(), "+359 888 123 456", "lookup-test");
        assertThat(found.code()).isEqualTo(booking.code());
        assertThat(found.cancellable()).isTrue();
    }

    @Test
    void wrongPhoneLooksLikeUnknownCode() {
        BookingDto booking = book("Street Cut", "Nia", "10:00", "0888123456");
        assertStatus(() -> bookingService.lookup(booking.code(), "0888999999", "wrong-phone-test"), HttpStatus.NOT_FOUND);
        assertStatus(() -> bookingService.lookup("FS-ZZZZZZ", "0888123456", "wrong-phone-test"), HttpStatus.NOT_FOUND);
    }

    @Test
    void cancellingFreesTheTime() {
        BookingDto booking = book("Street Cut", "Nia", "10:00", "0888123456");
        BookingDto cancelled = bookingService.cancel(booking.code(), "0888123456", "cancel-test");

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY)).contains(at(TUESDAY, "10:00"));
        assertStatus(() -> bookingService.cancel(booking.code(), "0888123456", "cancel-test"), HttpStatus.CONFLICT);
    }

    @Test
    void onlineCancellationClosesTwoHoursBefore() {
        BookingDto early = book("Street Cut", "Nia", "10:00", "0888123456");
        BookingDto late = book("Street Cut", "Rook", "10:00", "0888123456");

        clock.set(at(TUESDAY, "07:59"));
        assertThat(bookingService.cancel(early.code(), "0888123456", "cutoff-test").status())
                .isEqualTo(BookingStatus.CANCELLED);

        clock.set(at(TUESDAY, "08:00"));
        assertThat(bookingService.lookup(late.code(), "0888123456", "cutoff-test").cancellable()).isFalse();
        assertStatus(() -> bookingService.cancel(late.code(), "0888123456", "cutoff-test"), HttpStatus.CONFLICT);
    }

    @Test
    void tooManyWrongAttemptsAreBlockedForFifteenMinutes() {
        BookingDto booking = book("Street Cut", "Nia", "10:00", "0888123456");
        String ip = "rate-limit-test";
        for (int i = 0; i < 5; i++) {
            assertStatus(() -> bookingService.lookup(booking.code(), "0888000000", ip), HttpStatus.NOT_FOUND);
        }
        // Even the correct details are refused now
        assertStatus(() -> bookingService.lookup(booking.code(), "0888123456", ip), HttpStatus.TOO_MANY_REQUESTS);

        clock.advance(Duration.ofMinutes(16));
        assertThat(bookingService.lookup(booking.code(), "0888123456", ip).code()).isEqualTo(booking.code());
    }

    @Test
    void successfulLookupDoesNotResetTheFailureCount() {
        // An attacker mixing guesses with lookups of their own booking must still get blocked
        BookingDto own = book("Street Cut", "Nia", "10:00", "0888123456");
        String ip = "reset-bypass-test";
        for (int i = 0; i < 4; i++) {
            assertStatus(() -> bookingService.lookup("FS-ZZZZZZ", "0888123456", ip), HttpStatus.NOT_FOUND);
        }
        bookingService.lookup(own.code(), "0888123456", ip);
        assertStatus(() -> bookingService.lookup("FS-YYYYYY", "0888123456", ip), HttpStatus.NOT_FOUND);
        assertStatus(() -> bookingService.lookup(own.code(), "0888123456", ip), HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void expiredFailuresAreForgotten() {
        limiter.recordFailure("purge-test");
        assertThat(limiter.isTracking("purge-test")).isTrue();
        clock.advance(Duration.ofMinutes(16));
        limiter.purgeExpired();
        assertThat(limiter.isTracking("purge-test")).isFalse();
    }

    // --- Per-phone limit ---

    @Test
    void onePhoneCanHoldAtMostTwoUpcomingBookings() {
        book("Street Cut", "Melo", "10:00", "0888 555 555");
        BookingDto second = book("Street Cut", "Nia", "10:00", "+359888555555"); // same number, other format
        assertStatus(() -> book("Street Cut", "Rook", "12:00", "0888555555"), HttpStatus.CONFLICT);

        // Cancelling one frees a place
        bookingService.cancel(second.code(), "0888555555", "phone-limit-test");
        book("Street Cut", "Rook", "12:00", "0888555555");
    }

    @Test
    void pastBookingsDoNotCountTowardsThePhoneLimit() {
        book("Street Cut", "Nia", "10:00", "0888555555");
        book("Street Cut", "Rook", "10:00", "0888555555");

        clock.set(at(TUESDAY, "10:30")); // both appointments have started
        book("Street Cut", "Nia", "14:00", "0888555555");
    }

    // --- Privacy clean-up ---

    @Test
    void customerDetailsAreWiped24HoursAfterTheAppointment() {
        BookingDto booking = book("Street Cut", "Nia", "10:00", "0888123456"); // ends 11:00

        clock.set(at(TUESDAY.plusDays(1), "10:50")); // 23 h 50 min after the end
        anonymizationJob.run();
        assertThat(bookings.findByCode(booking.code()).orElseThrow().getPhone()).isNotNull();

        clock.set(at(TUESDAY.plusDays(1), "11:05")); // 24 h 5 min after the end
        anonymizationJob.run();
        Booking wiped = bookings.findByCode(booking.code()).orElseThrow();
        assertThat(wiped.isAnonymized()).isTrue();
        assertThat(wiped.getCustomerName()).isNull();
        assertThat(wiped.getPhone()).isNull();
        assertThat(wiped.getEmail()).isNull();
        assertThat(wiped.getNotes()).isNull();
        // Kept for the statistics
        assertThat(wiped.getServiceName()).isEqualTo("Street Cut");
        assertThat(wiped.getPriceAtBooking()).isEqualByComparingTo("28.00");

        // And the customer can no longer look it up with their phone
        assertStatus(() -> bookingService.lookup(booking.code(), "0888123456", "anon-test"), HttpStatus.NOT_FOUND);
    }

    @Test
    void phoneLimitHoldsForSimultaneousBookings() throws Exception {
        // Three bookings for one phone number sent at the same moment: only two may get through
        List<String> outcomes = simultaneously(List.of(
                () -> book("Street Cut", "Nia", "10:00", "0888 777 666"),
                () -> book("Street Cut", "Nia", "11:00", "0888 777 666"),
                () -> book("Street Cut", "Nia", "12:00", "0888 777 666")));
        assertThat(outcomes).containsExactlyInAnyOrder("booked", "booked", "PHONE_LIMIT_REACHED");
    }

    @Test
    void accountLimitHoldsForSimultaneousBookingsWithDifferentPhones() throws Exception {
        // One account, three phone numbers, three bookings at the same moment: still only two get through
        List<String> outcomes = simultaneously(List.of(
                () -> bookFromAccount("Nia", "10:00", "0888 700 001", "test-account"),
                () -> bookFromAccount("Nia", "11:00", "0888 700 002", "test-account"),
                () -> bookFromAccount("Nia", "12:00", "0888 700 003", "test-account")));
        assertThat(outcomes).containsExactlyInAnyOrder("booked", "booked", "PHONE_LIMIT_REACHED");
    }

    // --- Helpers ---

    private BookingDto book(String service, String artistName, String time, String phone) {
        String artistId = artistName == null ? null : artist(artistName).getId();
        return bookingService.create(new CreateBookingRequest(service(service).getId(), artistId, TUESDAY,
                LocalTime.parse(time), "Test Customer", phone, "test@example.com", "Keep some length on top."));
    }

    /** A Street Cut booked from the customer account {@code userId}. */
    private BookingDto bookFromAccount(String artistName, String time, String phone, String userId) {
        return bookingService.create(new CreateBookingRequest(service("Street Cut").getId(), artist(artistName).getId(),
                TUESDAY, LocalTime.parse(time), "Test Customer", phone, null, null), userId);
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

    private Booking rawBooking(String code, Instant start) {
        Booking b = new Booking();
        b.setCode(code);
        b.setArtistId(artist("Nia").getId());
        b.setServiceName("Raw");
        b.setPriceAtBooking(BigDecimal.TEN);
        b.setStartsAt(start);
        b.setEndsAt(start.plus(Duration.ofHours(1)));
        b.setSlots(List.of(start));
        b.setCreatedAt(NOW);
        return b;
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }
}
