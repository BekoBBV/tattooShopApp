package com.tattoostudio.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;

public interface BookingRepository extends MongoRepository<Booking, String> {

    Optional<Booking> findByCode(String code);

    Optional<Booking> findByPaymentId(String paymentId);

    /**
     * Bookings of these artists that hold their time and overlap [from, to), the break after them
     * included (their last slot starts before {@code to}): one query for a whole availability page.
     */
    @Query("{ 'artistId': { $in: ?0 }, 'holdsTime': true, 'startsAt': { $lt: ?2 }, 'slots': { $gte: ?1 } }")
    List<Booking> findHoldingTime(Collection<String> artistIds, Instant from, Instant to);

    /** Upcoming bookings a customer made themselves (flash and consultations), for the per-phone limit. */
    @Query(value = "{ 'phone': ?0, 'status': { $in: ['PENDING', 'BOOKED'] }, 'type': { $in: ['FLASH', 'CONSULTATION'] }, 'startsAt': { $gt: ?1 } }",
            count = true)
    long countUpcomingByPhone(String phone, Instant after);

    /** The same for one customer account, whatever phone numbers it used. */
    @Query(value = "{ 'userId': ?0, 'status': { $in: ['PENDING', 'BOOKED'] }, 'type': { $in: ['FLASH', 'CONSULTATION'] }, 'startsAt': { $gt: ?1 } }",
            count = true)
    long countUpcomingByUser(String userId, Instant after);

    /** The bookings of these artists with this status starting after {@code after}, earliest first. */
    List<Booking> findByArtistIdInAndStatusInAndStartsAtAfterOrderByStartsAtAsc(Collection<String> artistIds,
            Collection<BookingStatus> statuses, Instant after);

    /** Bookings of an artist with one of these statuses that have not ended yet. */
    long countByArtistIdAndStatusInAndEndsAtAfter(String artistId, Collection<BookingStatus> statuses, Instant after);

    /** Bookings that still claim a flash design (held, booked or tattooed). */
    List<Booking> findByFlashIdInAndClaimsFlashTrue(Collection<String> flashIds);

    /** Every booking an artist ever had (any status). */
    long countByArtistId(String artistId);
}
