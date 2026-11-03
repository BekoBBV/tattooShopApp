package com.tattoostudio.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tattoostudio.dto.AdminUserDto;
import com.tattoostudio.dto.AdminUserRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.util.Paging;
import com.tattoostudio.util.PhoneNumbers;

/**
 * The admin's users & rights page: every login (customers, artists, admins) with its role.
 * Safety rules: the admin can't change, block or delete their own login, and the shop always
 * keeps at least one active admin. Changing someone's role, artist or password signs them out at once.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final String STAFF_USERNAME = "[a-z0-9._-]{3,30}";
    static final Map<String, String> USER_SORTS = Map.of("username", "username", "name", "name", "role", "role",
            "created", "createdAt", "lastLogin", "lastLoginAt");

    private final UserRepository users;
    private final ArtistRepository artists;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongo;
    private final Clock clock;

    public AdminUserService(UserRepository users, ArtistRepository artists, PasswordEncoder passwordEncoder,
            MongoTemplate mongo, Clock clock) {
        this.users = users;
        this.artists = artists;
        this.passwordEncoder = passwordEncoder;
        this.mongo = mongo;
        this.clock = clock;
    }

    /** Logins matching the search text (username, name, email, phone), role and active state. */
    public PageDto<AdminUserDto> search(String text, Role role, Boolean active, ListQuery list) {
        List<Criteria> filters = Paging.filters();
        if (text != null) {
            filters.add(Paging.contains(text, "username", "name", "email", "phone"));
        }
        if (role != null) {
            filters.add(Criteria.where("role").is(role));
        }
        if (active != null) {
            filters.add(Criteria.where("active").is(active));
        }
        return Paging.find(mongo, User.class, filters, list, USER_SORTS, "role,asc", this::toDtos);
    }

    public AdminUserDto create(AdminUserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw invalid("Set a password of at least 8 characters");
        }
        User user = new User();
        user.setRole(request.role());
        user.setCreatedAt(clock.instant());
        if (request.role() == Role.USER) {
            applyCustomerDetails(user, request, true);
            user.setUsername(user.getEmail());
        } else {
            user.setUsername(staffUsername(request.username()));
            if (request.role() == Role.ARTIST) {
                user.setArtistId(freeArtist(request.artistId(), null).getId());
            }
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = save(user);
        log.info("Admin added {} login '{}'", saved.getRole(), saved.getUsername());
        return toDto(saved);
    }

    /** Changes a login's role, blocks / unblocks it and (for customers) their contact details. */
    public AdminUserDto update(String id, AdminUserRequest request, String adminUsername) {
        User user = user(id);
        Role oldRole = user.getRole();
        boolean oldActive = user.isActive();
        String oldArtistId = user.getArtistId();
        boolean roleChanged = request.role() != user.getRole();
        boolean blocking = Boolean.FALSE.equals(request.active()) && user.isActive();
        if (user.getUsername().equals(adminUsername) && (roleChanged || blocking)) {
            throw conflict("You can't change or block your own login. Ask another admin.");
        }
        if ((roleChanged || blocking) && isLastActiveAdmin(user)) {
            throw conflict("The shop needs at least one active admin. Make someone else admin first.");
        }
        if (roleChanged && request.role() == Role.USER && user.getEmail() == null) {
            throw conflict("Staff logins have no customer details. Create a customer account instead.");
        }
        if (request.role() == Role.ARTIST) {
            user.setArtistId(freeArtist(request.artistId(), user.getId()).getId());
        } else {
            user.setArtistId(null);
        }
        if (request.role() == Role.USER) {
            applyCustomerDetails(user, request, false);
        }
        user.setRole(request.role());
        if (request.active() != null) {
            user.setActive(request.active());
        }
        // A new role or artist signs the login out at once: their token no longer matches (see JwtConfig)
        boolean wasActiveAdmin = oldRole == Role.ADMIN && oldActive;
        User saved = save(user);
        if (wasActiveAdmin && (saved.getRole() != Role.ADMIN || !saved.isActive()) && activeAdmins() == 0) {
            saved.setRole(oldRole); // another admin was removed at the same moment: undo
            saved.setActive(oldActive);
            saved.setArtistId(oldArtistId);
            save(saved);
            throw conflict("The shop needs at least one active admin. Make someone else admin first.");
        }
        log.info("Admin set '{}' to {}{}", saved.getUsername(), saved.getRole(), saved.isActive() ? "" : " (blocked)");
        return toDto(saved);
    }

    public void setPassword(String id, String password) {
        User user = user(id);
        // Only these fields are written, so a sign-in or another change at the same moment is kept
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update()
                .set("passwordHash", passwordEncoder.encode(password))
                .set("passwordChangedAt", clock.instant()), User.class); // signs out sessions using the old password
        log.info("Admin reset the password of '{}'", user.getUsername());
    }

    /** Deletes a login. A customer's past bookings stay (for the shop's records) but leave the account. */
    public void delete(String id, String adminUsername) {
        User user = user(id);
        if (user.getUsername().equals(adminUsername)) {
            throw conflict("You can't delete your own login.");
        }
        if (isLastActiveAdmin(user)) {
            throw conflict("The shop needs at least one active admin.");
        }
        users.delete(user);
        if (user.getRole() == Role.ADMIN && user.isActive() && activeAdmins() == 0) {
            users.save(user); // another admin was removed at the same moment: undo
            throw conflict("The shop needs at least one active admin.");
        }
        mongo.updateMulti(Query.query(Criteria.where("userId").is(user.getId())), new Update().unset("userId"), Booking.class);
        log.info("Admin deleted login '{}'", user.getUsername());
    }

    private boolean isLastActiveAdmin(User user) {
        if (user.getRole() != Role.ADMIN || !user.isActive()) {
            return false;
        }
        return activeAdmins() <= 1;
    }

    private long activeAdmins() {
        return mongo.count(Query.query(Criteria.where("role").is(Role.ADMIN).and("active").is(true)), User.class);
    }

    private void applyCustomerDetails(User user, AdminUserRequest r, boolean creating) {
        if (blank(r.name()) || blank(r.phone()) || (creating && blank(r.email()))) {
            throw invalid("Customers need a name, " + (creating ? "an email and " : "") + "a phone number");
        }
        try {
            PhoneNumbers.normalize(r.phone());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }
        user.setName(r.name().trim());
        user.setPhone(r.phone().trim());
        if (creating) {
            user.setEmail(AuthService.normalize(r.email()));
        }
    }

    private static String staffUsername(String username) {
        String normalized = username == null ? "" : AuthService.normalize(username);
        if (!normalized.matches(STAFF_USERNAME)) {
            throw invalid("Staff usernames are 3-30 letters, digits, dots, dashes or underscores");
        }
        return normalized;
    }

    /** The artist a ARTIST login belongs to; each artist has at most one login. */
    private Artist freeArtist(String artistId, String userId) {
        if (blank(artistId)) {
            throw invalid("Pick the artist this login belongs to");
        }
        Artist artist = artists.findById(artistId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.ARTIST_NOT_FOUND, "Artist not found"));
        users.findByArtistId(artistId)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw conflict(artist.getName() + " already has a login (" + other.getUsername() + ")");
                });
        return artist;
    }

    private User save(User user) {
        users.findByUsername(user.getUsername())
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    throw taken(user.getUsername());
                });
        try {
            return users.save(user);
        } catch (DuplicateKeyException e) {
            throw taken(user.getUsername()); // taken a moment ago
        }
    }

    private User user(String id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Login not found"));
    }

    private List<AdminUserDto> toDtos(List<User> page) {
        Map<String, String> artistNames = artists.findAll().stream()
                .collect(Collectors.toMap(Artist::getId, Artist::getName));
        return page.stream().map(u -> AdminUserDto.from(u, artistNames.get(u.getArtistId()))).toList();
    }

    private AdminUserDto toDto(User u) {
        return AdminUserDto.from(u, u.getArtistId() == null ? null
                : artists.findById(u.getArtistId()).map(Artist::getName).orElse(null));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ApiException taken(String username) {
        return conflict("The username " + username + " is already taken");
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}
