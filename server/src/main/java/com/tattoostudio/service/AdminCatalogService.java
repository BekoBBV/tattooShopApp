package com.tattoostudio.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
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
import com.tattoostudio.dto.AdminServiceDto;
import com.tattoostudio.dto.ArtistLoginRequest;
import com.tattoostudio.dto.ArtistOptionDto;
import com.tattoostudio.dto.ArtistRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ServiceRequest;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.ServiceItemRepository;
import com.tattoostudio.repository.UserRepository;

/**
 * The admin's side of services and artists. Usually services are hidden and artists deactivated
 * (PUT with active: false). Deleting for good is possible when nothing depends on them: a service
 * without upcoming bookings (past bookings keep their own copy of the name and price), an artist
 * without any bookings (so history and stats stay complete).
 */
@Service
public class AdminCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogService.class);

    private final ServiceItemRepository services;
    private final ArtistRepository artists;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final ScheduleService schedules;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final MongoTemplate mongo;
    private final CatalogService catalog;

    public AdminCatalogService(ServiceItemRepository services, ArtistRepository artists, BookingRepository bookings,
            UserRepository users, ScheduleService schedules, PasswordEncoder passwordEncoder, Clock clock,
            MongoTemplate mongo, CatalogService catalog) {
        this.services = services;
        this.artists = artists;
        this.bookings = bookings;
        this.users = users;
        this.schedules = schedules;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.mongo = mongo;
        this.catalog = catalog;
    }

    // ---- Services ----

    /** All services (hidden ones too) matching the search text, price range and shown/hidden state. */
    public PageDto<AdminServiceDto> searchServices(String text, Boolean active, BigDecimal minPrice, BigDecimal maxPrice,
            ListQuery list) {
        return catalog.searchServices(text, active, minPrice, maxPrice, list)
                .map(page -> page.stream().map(AdminServiceDto::from).toList());
    }

    public AdminServiceDto createService(ServiceRequest request) {
        ServiceItem service = new ServiceItem();
        service.setSortOrder(services.findAll().stream().mapToInt(ServiceItem::getSortOrder).max().orElse(0) + 1);
        apply(service, request);
        log.info("Added service {}", service.getName());
        return AdminServiceDto.from(services.save(service));
    }

    public AdminServiceDto updateService(String id, ServiceRequest request) {
        ServiceItem service = service(id);
        apply(service, request);
        return AdminServiceDto.from(services.save(service));
    }

    /** Shows or hides the service on the booking page; only the active flag is written. */
    public AdminServiceDto setServiceActive(String id, boolean active) {
        service(id);
        setActive(id, active, ServiceItem.class);
        return AdminServiceDto.from(service(id));
    }

    /** Deletes the service for good. Refused while it has upcoming bookings; past ones keep its name and price. */
    public void deleteService(String id) {
        ServiceItem service = service(id);
        long upcoming = bookings.countByServiceIdAndStatusAndEndsAtAfter(id, BookingStatus.BOOKED, clock.instant());
        if (upcoming > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, service.getName() + " has " + upcoming
                    + (upcoming == 1 ? " upcoming booking" : " upcoming bookings") + ". Hide it instead, or cancel them first.");
        }
        services.delete(service);
        log.info("Deleted service {}", service.getName());
    }

    private void apply(ServiceItem service, ServiceRequest r) {
        service.setName(r.name().trim());
        service.setDescription(trimToEmpty(r.description()));
        service.setPrice(r.price());
        service.setLabel(trimToEmpty(r.label()));
        service.setPalette(r.palette());
        if (r.active() != null) {
            service.setActive(r.active());
        }
    }

    private ServiceItem service(String id) {
        return services.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.SERVICE_NOT_FOUND, "Service not found"));
    }

    // ---- Artists ----

    /** All artists (inactive ones too) matching the search text, working day and active state. */
    public PageDto<AdminArtistDto> searchArtists(String text, Boolean active, DayOfWeek day, ListQuery list) {
        return catalog.searchArtists(text, active, day, list).map(this::toDtos);
    }

    /** Every artist (inactive ones too) by name, with their login name: for the admin's pickers. */
    public List<ArtistOptionDto> artistOptions() {
        List<Artist> all = artists.findAll(Sort.by("name"));
        Map<String, String> usernames = users.findByArtistIdIn(all.stream().map(Artist::getId).toList()).stream()
                .collect(Collectors.toMap(User::getArtistId, User::getUsername, (a, b) -> a));
        return all.stream().map(b -> new ArtistOptionDto(b.getId(), b.getName(), b.isActive(), usernames.get(b.getId())))
                .toList();
    }

    /** One page of artists with their login names and clashes, loaded with one query each. */
    private List<AdminArtistDto> toDtos(List<Artist> page) {
        Map<String, String> usernames = users.findByArtistIdIn(page.stream().map(Artist::getId).toList()).stream()
                .collect(Collectors.toMap(User::getArtistId, User::getUsername, (a, b) -> a));
        Map<String, List<StaffBookingDto>> conflicts = schedules.conflicts(page);
        return page.stream()
                .map(b -> AdminArtistDto.from(b, schedules.upcoming(b.getExceptions()), usernames.get(b.getId()),
                        conflicts.getOrDefault(b.getId(), List.of())))
                .toList();
    }

    public AdminArtistDto createArtist(ArtistRequest request) {
        Artist artist = new Artist();
        artist.setSortOrder(artists.findAll().stream().mapToInt(Artist::getSortOrder).max().orElse(0) + 1);
        apply(artist, request);
        log.info("Added artist {}", artist.getName());
        return toDto(artists.save(artist), null);
    }

    public AdminArtistDto updateArtist(String id, ArtistRequest request) {
        Artist artist = artist(id);
        if (Boolean.FALSE.equals(request.active()) && artist.isActive()) {
            requireNoUpcomingBookings(artist);
        }
        apply(artist, request);
        return toDto(artists.save(artist), username(id));
    }

    /**
     * Puts the artist on or takes them off the booking page; while inactive their login can't sign in.
     * Deactivating is refused while they still have upcoming bookings: those need to be cancelled first.
     * Only the active flag is written, so hours the artist saved meanwhile are kept.
     */
    public AdminArtistDto setArtistActive(String id, boolean active) {
        Artist artist = artist(id);
        if (!active && artist.isActive()) {
            requireNoUpcomingBookings(artist);
        }
        setActive(id, active, Artist.class);
        return toDto(artist(id), username(id));
    }

    /** Deletes an artist who never had a booking, together with their login. */
    public void deleteArtist(String id) {
        Artist artist = artist(id);
        long history = bookings.countByArtistId(id);
        if (history > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, artist.getName() + " has " + history
                    + (history == 1 ? " booking" : " bookings") + " in the shop's history. Deactivate them instead.");
        }
        users.findByArtistId(id).ifPresent(users::delete);
        artists.delete(artist);
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

    /** Writes only the active flag, so nothing else edited at the same moment is overwritten. */
    private void setActive(String id, boolean active, Class<?> type) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("active", active), type);
    }

    private void requireNoUpcomingBookings(Artist artist) {
        long upcoming = bookings.countByArtistIdAndStatusAndEndsAtAfter(artist.getId(), BookingStatus.BOOKED, clock.instant());
        if (upcoming > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, artist.getName() + " still has " + upcoming
                    + (upcoming == 1 ? " upcoming booking" : " upcoming bookings") + ". Cancel them first.");
        }
    }

    private void apply(Artist artist, ArtistRequest r) {
        artist.setName(r.name().trim());
        artist.setHandle(trimToEmpty(r.handle()));
        artist.setSpecialty(trimToEmpty(r.specialty()));
        artist.setBio(trimToEmpty(r.bio()));
        artist.setPhotoUrl(r.photoUrl() == null || r.photoUrl().isBlank() ? null : r.photoUrl().trim());
        artist.setAccent(r.accent());
        artist.setWeeklyHours(schedules.cleanWeeklyHours(r.weeklyHours()));
        artist.setExceptions(new ArrayList<>(schedules.cleanExceptions(r.exceptions())));
        if (r.active() != null) {
            artist.setActive(r.active());
        }
    }

    private AdminArtistDto toDto(Artist b, String username) {
        return AdminArtistDto.from(b, schedules.upcoming(b.getExceptions()), username, schedules.conflicts(b));
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
