package com.tattoostudio.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.StatsDto;
import com.tattoostudio.dto.StatsDto.ArtistStats;
import com.tattoostudio.dto.StatsDto.BusiestArtist;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.repository.ArtistRepository;

/** Numbers for the admin overview. A small shop has few bookings, so they are counted in memory. */
@Service
public class StatsService {

    private static final int MAX_RANGE_DAYS = 366;

    private final MongoTemplate mongo;
    private final ArtistRepository artists;
    private final BookingRules rules;
    private final Clock clock;

    public StatsService(MongoTemplate mongo, ArtistRepository artists, BookingRules rules, Clock clock) {
        this.mongo = mongo;
        this.artists = artists;
        this.rules = rules;
        this.clock = clock;
    }

    /** Stats for bookings starting from..to (inclusive). Default: this week, Monday to Sunday. */
    public StatsDto stats(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        LocalDate start = from != null ? from : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Pick a date range of at most " + MAX_RANGE_DAYS + " days, with 'from' before 'to'");
        }
        List<Booking> found = mongo.find(Query.query(Criteria.where("startsAt")
                .gte(start.atStartOfDay(rules.timezone()).toInstant())
                .lt(end.plusDays(1).atStartOfDay(rules.timezone()).toInstant())), Booking.class);

        Map<String, List<Booking>> byArtist = found.stream().collect(Collectors.groupingBy(Booking::getArtistId));
        List<ArtistStats> perArtist = new ArrayList<>();
        // Every active artist, plus inactive ones who still had bookings in this period
        for (Artist artist : artists.findAll().stream().sorted(Comparator.comparingInt(Artist::getSortOrder)).toList()) {
            List<Booking> own = byArtist.getOrDefault(artist.getId(), List.of());
            if (artist.isActive() || !own.isEmpty()) {
                perArtist.add(new ArtistStats(artist.getId(), artist.getName(), countNotCancelled(own),
                        count(own, BookingStatus.DONE), count(own, BookingStatus.NO_SHOW), revenue(own)));
            }
        }
        BusiestArtist busiest = perArtist.stream()
                .filter(s -> s.bookings() > 0)
                .max(Comparator.comparingLong(ArtistStats::bookings))
                .map(s -> new BusiestArtist(s.artistId(), s.name(), s.bookings()))
                .orElse(null);

        return new StatsDto(start, end, countNotCancelled(found), count(found, BookingStatus.DONE),
                count(found, BookingStatus.NO_SHOW), count(found, BookingStatus.CANCELLED), revenue(found),
                busiest, perArtist);
    }

    private static long count(List<Booking> list, BookingStatus status) {
        return list.stream().filter(b -> b.getStatus() == status).count();
    }

    private static long countNotCancelled(List<Booking> list) {
        return list.stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED).count();
    }

    /** Revenue counts finished (DONE) appointments only, at the price when they were booked. */
    private static BigDecimal revenue(List<Booking> list) {
        return list.stream()
                .filter(b -> b.getStatus() == BookingStatus.DONE && b.getPriceAtBooking() != null)
                .map(Booking::getPriceAtBooking)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
