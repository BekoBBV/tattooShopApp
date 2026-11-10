package com.tattoostudio.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.dto.AdminFlashDto;
import com.tattoostudio.dto.ArtistDto;
import com.tattoostudio.dto.FlashDto;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.WorkDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Review;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.FlashRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.repository.WorkRepository;
import com.tattoostudio.util.Paging;

/** What customers can see and book: active artists, the flash sheet and the artists' work. */
@Service
public class CatalogService {

    private final ArtistRepository artists;
    private final FlashRepository flash;
    private final WorkRepository works;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final MongoTemplate mongo;
    private final BookingRules rules;
    private final Clock clock;

    public CatalogService(ArtistRepository artists, FlashRepository flash, WorkRepository works,
            BookingRepository bookings, ReviewRepository reviews, MongoTemplate mongo, BookingRules rules, Clock clock) {
        this.artists = artists;
        this.flash = flash;
        this.works = works;
        this.bookings = bookings;
        this.reviews = reviews;
        this.mongo = mongo;
        this.rules = rules;
        this.clock = clock;
    }

    static final Map<String, String> ARTIST_SORTS = Map.of("order", "sortOrder", "name", "name");
    static final Map<String, String> FLASH_SORTS = Map.of("order", "sortOrder", "name", "name", "price", "price",
            "time", "durationMinutes", "created", "createdAt");

    // ---- Artists ----

    public List<Artist> activeArtists() {
        return artists.findByActiveTrueOrderBySortOrderAsc();
    }

    /** @throws ApiException 404 if the artist does not exist or is inactive */
    public Artist activeArtist(String id) {
        return artists.findById(id)
                .filter(Artist::isActive)
                .orElseThrow(CatalogService::artistNotFound);
    }

    /** An active artist by page name ("kai") or id. */
    public Artist activeArtistBySlugOrId(String key) {
        return artists.findBySlug(key.toLowerCase()).or(() -> artists.findById(key))
                .filter(Artist::isActive)
                .orElseThrow(CatalogService::artistNotFound);
    }

    /**
     * Artists matching the search text (name, style, bio) who work on {@code day}; {@code active}
     * true for the artists on the site, false for deactivated ones, null for both (admin).
     */
    public PageDto<Artist> searchArtists(String text, Boolean active, DayOfWeek day, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name", "style", "bio"));
        }
        if (day != null) {
            filters.add(Criteria.where("weeklyHours.day").is(day));
        }
        return Paging.find(mongo, Artist.class, filters, list, ARTIST_SORTS, "order,asc", page -> page);
    }

    /** Artists as customers see them, with their ratings (one query for all reviews). */
    public List<ArtistDto> artistDtos(List<Artist> list) {
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        Map<String, List<Review>> approved = reviews.findByApprovedTrueOrderByCreatedAtDesc().stream()
                .filter(r -> r.getArtistId() != null)
                .collect(Collectors.groupingBy(Review::getArtistId));
        return list.stream().map(a -> {
            List<Review> own = approved.getOrDefault(a.getId(), List.of());
            return ArtistDto.from(a, today, average(own), own.size());
        }).toList();
    }

    public ArtistDto artistDto(Artist artist) {
        return artistDtos(List.of(artist)).get(0);
    }

    /** Average rating rounded to one decimal, or null without reviews. */
    public static Double average(List<Review> list) {
        if (list.isEmpty()) {
            return null;
        }
        double avg = list.stream().mapToInt(Review::getRating).average().orElse(0);
        return Math.round(avg * 10) / 10.0;
    }

    public List<WorkDto> works(String artistId) {
        return works.findByArtistIdOrderByCreatedAtDesc(artistId).stream().map(WorkDto::from).toList();
    }

    // ---- Flash ----

    /** @throws ApiException 404 if there is no such design */
    public FlashDesign flash(String id) {
        return flash.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FLASH_NOT_FOUND,
                "This flash design doesn't exist (any more)"));
    }

    /** The bookings that keep these designs off the sheet, by design id (a lapsed deposit hold doesn't count). */
    public Map<String, Booking> claims(Collection<String> flashIds) {
        Instant now = clock.instant();
        Map<String, Booking> result = new HashMap<>();
        if (flashIds.isEmpty()) {
            return result;
        }
        for (Booking b : bookings.findByFlashIdInAndClaimsFlashTrue(flashIds)) {
            if (!AvailabilityService.isLapsedHold(b, now)) {
                result.put(b.getFlashId(), b);
            }
        }
        return result;
    }

    public boolean isClaimed(String flashId) {
        return claims(List.of(flashId)).containsKey(flashId);
    }

    /**
     * The flash sheet: designs that can be booked now (switched on, not booked, by an active artist),
     * by artist order and then the artist's own order. Optionally one artist or one size only.
     */
    public List<FlashDto> sheet(String artistId, FlashDesign.Size size) {
        Map<String, Artist> active = activeArtists().stream().collect(Collectors.toMap(Artist::getId, Function.identity()));
        List<FlashDesign> candidates = mongo.find(org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("available").is(true).and("artistId").in(active.keySet())), FlashDesign.class);
        Map<String, Booking> claimed = claims(candidates.stream().map(FlashDesign::getId).toList());
        return candidates.stream()
                .filter(f -> !claimed.containsKey(f.getId()))
                .filter(f -> artistId == null || artistId.isBlank() || f.getArtistId().equals(artistId))
                .filter(f -> size == null || f.size() == size)
                .sorted(Comparator.comparingInt((FlashDesign f) -> active.get(f.getArtistId()).getSortOrder())
                        .thenComparingInt(FlashDesign::getSortOrder).thenComparing(FlashDesign::getName))
                .map(f -> toDto(f, active.get(f.getArtistId()), true))
                .toList();
    }

    /** One design for the booking page; {@code bookable} says whether it can still be booked. */
    public FlashDto flashDto(String id) {
        FlashDesign f = flash(id);
        Artist artist = artists.findById(f.getArtistId()).orElse(null);
        boolean bookable = artist != null && artist.isActive() && f.isAvailable() && !isClaimed(f.getId());
        return toDto(f, artist, bookable);
    }

    private FlashDto toDto(FlashDesign f, Artist artist, boolean bookable) {
        return new FlashDto(f.getId(), f.getArtistId(), artist == null ? null : artist.getName(), f.getName(),
                f.getWidthCm(), f.getHeightCm(), f.size(), f.getDurationMinutes(), f.getPrice(),
                rules.flashDeposit().min(f.getPrice()), f.getImage(), bookable);
    }

    /**
     * Designs for the staff pages: one artist's (or everyone's when {@code artistId} is null), with the
     * switch state and search text (name).
     */
    public PageDto<AdminFlashDto> searchFlash(String artistId, Boolean available, String text, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (artistId != null) {
            filters.add(Criteria.where("artistId").is(artistId));
        }
        if (available != null) {
            filters.add(Criteria.where("available").is(available));
        }
        if (text != null) {
            filters.add(Paging.contains(text, "name"));
        }
        return Paging.find(mongo, FlashDesign.class, filters, list, FLASH_SORTS, "order,asc", this::adminDtos);
    }

    public List<AdminFlashDto> adminDtos(List<FlashDesign> page) {
        Map<String, Booking> claimed = claims(page.stream().map(FlashDesign::getId).toList());
        Map<String, String> names = artists.findAllById(page.stream().map(FlashDesign::getArtistId).distinct().toList())
                .stream().collect(Collectors.toMap(Artist::getId, Artist::getName));
        return page.stream().map(f -> {
            Booking b = claimed.get(f.getId());
            AdminFlashDto.State state = b == null ? (f.isAvailable() ? AdminFlashDto.State.ON_SHEET : AdminFlashDto.State.HIDDEN)
                    : b.getStatus() == BookingStatus.PENDING ? AdminFlashDto.State.HELD
                    : b.getStatus() == BookingStatus.DONE ? AdminFlashDto.State.TATTOOED
                    : AdminFlashDto.State.BOOKED;
            return new AdminFlashDto(f.getId(), f.getArtistId(), names.get(f.getArtistId()), f.getName(), f.getWidthCm(),
                    f.getHeightCm(), f.size(), f.getDurationMinutes(), f.getPrice(), f.getImage(), f.isAvailable(), state,
                    b == null ? null : b.getCode(), f.getSortOrder(), f.getCreatedAt());
        }).toList();
    }

    static ApiException artistNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found");
    }
}
