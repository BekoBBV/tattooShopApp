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

    /** Bookings of one artist that overlap the time range [from, to). */
    List<Booking> findByArtistIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(
            String artistId, BookingStatus status, Instant to, Instant from);

    /** Active bookings of the given artists that overlap [from, to): one query for a whole availability page. */
    @Query("{ 'artistId': { $in: ?0 }, 'status': 'BOOKED', 'startsAt': { $lt: ?2 }, 'endsAt': { $gt: ?1 } }")
    List<Booking> findActiveOverlapping(Collection<String> artistIds, Instant from, Instant to);

    /** Upcoming bookings of one customer account, used for the per-account limit. */
    long countByUserIdAndStatusAndStartsAtAfter(String userId, BookingStatus status, Instant after);

    /** Upcoming bookings of one customer, used for the per-phone limit. */
    long countByPhoneAndStatusAndStartsAtAfter(String phone, BookingStatus status, Instant after);

    /** The bookings of these artists with this status starting after {@code after}, earliest first. */
    List<Booking> findByArtistIdInAndStatusAndStartsAtAfterOrderByStartsAtAsc(Collection<String> artistIds,
            BookingStatus status, Instant after);

    /** Bookings of an artist with this status that have not ended yet. */
    long countByArtistIdAndStatusAndEndsAtAfter(String artistId, BookingStatus status, Instant after);

    /** Bookings of a service with this status that have not ended yet. */
    long countByServiceIdAndStatusAndEndsAtAfter(String serviceId, BookingStatus status, Instant after);

    /** Every booking an artist ever had (any status). */
    long countByArtistId(String artistId);
}
