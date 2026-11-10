package com.tattoostudio.service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tattoostudio.dto.AdminArtistDto;
import com.tattoostudio.dto.ArtistLoginRequest;
import com.tattoostudio.dto.ArtistOptionDto;
import com.tattoostudio.dto.ArtistRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.model.Work;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.FlashRepository;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.repository.WorkRepository;
import com.tattoostudio.storage.ImageUploads;

/**
 * The admin's side of artists. Usually artists are deactivated (PUT with active: false). Deleting
 * for good is possible for an artist without any bookings (so history and stats stay complete),
 * together with their login, flash designs and portfolio.
 */
@Service
public class AdminCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogService.class);

    private final ArtistRepository artists;
    private final BookingRepository bookings;
    private final FlashRepository flash;
    private final WorkRepository works;
    private final UserRepository users;
    private final ScheduleService schedules;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final MongoTemplate mongo;
    private final CatalogService catalog;
    private final ImageUploads images;

    public AdminCatalogService(ArtistRepository artists, BookingRepository bookings, FlashRepository flash,
            WorkRepository works, UserRepository users, ScheduleService schedules, PasswordEncoder passwordEncoder,
            Clock clock, MongoTemplate mongo, CatalogService catalog, ImageUploads images) {
        this.artists = artists;
        this.bookings = bookings;
        this.flash = flash;
        this.works = works;
        this.users = users;
        this.schedules = schedules;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.mongo = mongo;
        this.catalog = catalog;
        this.images = images;
    }

    /** All artists (inactive ones too) matching the search text, working day and active state. */
    public PageDto<AdminArtistDto> searchArtists(String text, Boolean active, DayOfWeek day, ListQuery list) {
        return catalog.searchArtists(text, active, day, list).map(this::toDtos);
    }

    /** Every artist (inactive ones too) by name, with their login name: for the admin's pickers. */
    public List<ArtistOptionDto> artistOptions() {
        List<Artist> all = artists.findAll(Sort.by("name"));
        Map<String, String> usernames = users.findByArtistIdIn(all.stream().map(Artist::getId).toList()).stream()
                .collect(Collectors.toMap(User::getArtistId, User::getUsername, (a, b) -> a));
        return all.stream().map(a -> new ArtistOptionDto(a.getId(), a.getName(), a.isActive(), usernames.get(a.getId())))
                .toList();
    }

    /** One page of artists with their login names and clashes, loaded with one query each. */
    private List<AdminArtistDto> toDtos(List<Artist> page) {
        Map<String, String> usernames = users.findByArtistIdIn(page.stream().map(Artist::getId).toList()).stream()
                .collect(Collectors.toMap(User::getArtistId, User::getUsername, (a, b) -> a));
        Map<String, List<StaffBookingDto>> conflicts = schedules.conflicts(page);
        return page.stream()
                .map(a -> AdminArtistDto.from(a, schedules.upcoming(a.getExceptions()), usernames.get(a.getId()),
                        conflicts.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    public AdminArtistDto createArtist(ArtistRequest request) {
        Artist artist = new Artist();
        artist.setSortOrder(artists.findAll().stream().mapToInt(Artist::getSortOrder).max().orElse(0) + 1);
        apply(artist, request);
        log.info("Added artist {}", artist.getName());
        return toDto(save(artist), null);
    }

    public AdminArtistDto updateArtist(String id, ArtistRequest request) {
        Artist artist = artist(id);
        if (Boolean.FALSE.equals(request.active()) && artist.isActive()) {
            requireNoUpcomingBookings(artist);
        }
        apply(artist, request);
        return toDto(save(artist), username(id));
    }

    /**
     * Puts the artist on or takes them off the site; while inactive their login can't sign in and their
     * flash designs are off the sheet. Deactivating is refused while they still have upcoming bookings:
     * those need to be cancelled first. Only the active flag is written, so hours the artist saved
     * meanwhile are kept.
     */
    public AdminArtistDto setArtistActive(String id, boolean active) {
        Artist artist = artist(id);
        if (!active && artist.isActive()) {
            requireNoUpcomingBookings(artist);
        }
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("active", active), Artist.class);
        return toDto(artist(id), username(id));
    }

    /** Deletes an artist who never had a booking, with their login, flash designs and portfolio. */
    public void deleteArtist(String id) {
        Artist artist = artist(id);
        long history = bookings.countByArtistId(id);
        if (history > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, artist.getName() + " has " + history
                    + (history == 1 ? " booking" : " bookings") + " in the studio's history. Deactivate them instead.");
        }
        users.findByArtistId(id).ifPresent(users::delete);
        List<FlashDesign> designs = flash.findByArtistIdOrderBySortOrderAsc(id);
        List<Work> portfolio = works.findByArtistIdOrderByCreatedAtDesc(id);
        flash.deleteAll(designs);
        works.deleteAll(portfolio);
        artists.delete(artist);
        designs.forEach(d -> images.delete(d.getImage()));
        portfolio.forEach(w -> images.delete(w.getImage()));
        images.delete(artist.getSignaturePiece());
        log.info("Deleted artist {}", artist.getName());
    }

    /**
     * Creates the artist's login, or changes the username and password of the existing one. A login
     * the admin blocked (Users & rights) stays blocked.
     */
    public String setArtistLogin(String artistId, ArtistLoginRequest request) {
        artist(artistId);
        String username = AuthService.normalize(request.username());
        User login = users.findByArtistId(artistId).orElseGet(() -> {
            User created = new User(null, null, Role.ARTIST, artistId);
            created.setCreatedAt(clock.instant());
            return created;
        });
        users.findByUsername(username)
                .filter(other -> !other.getId().equals(login.getId()))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "The username " + username + " is already taken");
                });
        login.setUsername(username);
        login.setPasswordHash(passwordEncoder.encode(request.password()));
        login.setPasswordChangedAt(clock.instant()); // signs out sessions that used the old password
        try {
            users.save(login);
        } catch (DuplicateKeyException e) {
            // Someone took the username a moment ago (the unique index caught it)
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "The username " + username + " is already taken");
        }
        log.info("Set login '{}' for artist {}", username, artistId);
        return username;
    }

    private void requireNoUpcomingBookings(Artist artist) {
        long upcoming = bookings.countByArtistIdAndStatusInAndEndsAtAfter(artist.getId(),
                List.of(BookingStatus.PENDING, BookingStatus.BOOKED), clock.instant());
        if (upcoming > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, artist.getName() + " still has " + upcoming
                    + (upcoming == 1 ? " upcoming booking" : " upcoming bookings") + ". Cancel them first.");
        }
    }

    private void apply(Artist artist, ArtistRequest r) {
        artist.setName(r.name().trim());
        // The page address only changes when the admin changes it, so old links keep working
        if (r.slug() != null && !r.slug().isBlank()) {
            artist.setSlug(r.slug().trim());
        } else if (artist.getSlug() == null) {
            String fromName = slugOf(r.name());
            artist.setSlug(fromName != null ? fromName : "artist-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }
        artist.setStyle(trimToEmpty(r.style()));
        artist.setBio(trimToEmpty(r.bio()));
        artist.setHourlyRate(r.hourlyRate());
        artist.setDayRate(r.dayRate());
        if (r.breakMinutes() != null) {
            artist.setBreakMinutes(schedules.cleanBreak(r.breakMinutes()));
        }
        artist.setWeeklyHours(schedules.cleanWeeklyHours(r.weeklyHours()));
        artist.setExceptions(new ArrayList<>(schedules.cleanExceptions(r.exceptions())));
        if (r.active() != null) {
            artist.setActive(r.active());
        }
    }

    /** "Kai" → "kai", "Ana María" → "ana-maria". */
    static String slugOf(String name) {
        String plain = Normalizer.normalize(name.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? null : slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private Artist save(Artist artist) {
        try {
            return artists.save(artist);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "Another artist already uses the page address /artists/" + artist.getSlug());
        }
    }

    private AdminArtistDto toDto(Artist a, String username) {
        return AdminArtistDto.from(a, schedules.upcoming(a.getExceptions()), username, schedules.conflicts(a));
    }

    private String username(String artistId) {
        return users.findByArtistId(artistId).map(User::getUsername).orElse(null);
    }

    private Artist artist(String id) {
        return artists.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
