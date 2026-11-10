package com.tattoostudio.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.tattoostudio.config.BookingRules;
import com.tattoostudio.config.JwtConfig;
import com.tattoostudio.dto.AuthUserDto;
import com.tattoostudio.dto.LoginResponse;
import com.tattoostudio.dto.RegisterRequest;
import com.tattoostudio.dto.UpdateAccountRequest;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.util.PhoneNumbers;
import com.tattoostudio.util.SlidingWindowCounter;

/**
 * Sign-in for everyone (staff with a username, customers with their email) and customer sign-up.
 * A token is valid for app.auth.token-lifetime (12 h).
 * Guessing is slowed down twice: one visitor (IP address) gets 10 failed sign-ins per 15 minutes
 * across all usernames, and one username is blocked for 5 minutes after 5 wrong passwords. The
 * username block is short so that someone trying on purpose can't keep an artist locked out for long.
 * One visitor can create at most app.limits.signups-per-hour accounts per hour.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final int MAX_FAILED_PER_USERNAME = 5;
    private static final Duration USERNAME_WINDOW = Duration.ofMinutes(5);
    static final int MAX_FAILED_PER_VISITOR = 10;
    private static final Duration VISITOR_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final ArtistRepository artists;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final MongoTemplate mongo;
    private final Clock clock;
    /** Customer accounts are for adults, with the same age and time zone as the bookings. */
    private final BookingRules rules;
    private final Duration tokenLifetime;
    private final int maxSignupsPerHour;
    private final SlidingWindowCounter failedByUsername;
    private final SlidingWindowCounter failedByVisitor;
    private final SlidingWindowCounter signupsByVisitor;
    /** Checked when the username does not exist, so a wrong username takes as long as a wrong password. */
    private final String dummyHash;

    public AuthService(UserRepository users, ArtistRepository artists, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            MongoTemplate mongo, Clock clock, BookingRules rules,
            @Value("${app.auth.token-lifetime}") Duration tokenLifetime,
            @Value("${app.limits.signups-per-hour:5}") int maxSignupsPerHour) {
        this.users = users;
        this.artists = artists;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.mongo = mongo;
        this.clock = clock;
        this.rules = rules;
        this.tokenLifetime = tokenLifetime;
        this.maxSignupsPerHour = maxSignupsPerHour;
        this.failedByUsername = new SlidingWindowCounter(USERNAME_WINDOW, clock);
        this.failedByVisitor = new SlidingWindowCounter(VISITOR_WINDOW, clock);
        this.signupsByVisitor = new SlidingWindowCounter(Duration.ofHours(1), clock);
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** @param visitor the caller's IP address */
    public LoginResponse login(String username, String password, String visitor) {
        String key = normalize(username);
        if (failedByVisitor.count(visitor) >= MAX_FAILED_PER_VISITOR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many failed sign-ins from this device. Please wait 15 minutes and try again.");
        }
        if (failedByUsername.count(key) >= MAX_FAILED_PER_USERNAME) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many wrong passwords for this account. Please wait 5 minutes and try again.");
        }
        User user = users.findByUsername(key).filter(this::canSignIn).orElse(null);
        boolean passwordOk = passwordEncoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !passwordOk) {
            failedByUsername.record(key);
            failedByVisitor.record(visitor);
            log.info("Failed login for '{}' from {}", key, visitor);
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_LOGIN,
                    key.contains("@") ? "Wrong email or password." : "Wrong username or password.");
        }
        // Only this field is written, so a password reset at the same moment is never undone
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId())),
                new Update().set("lastLoginAt", clock.instant()).inc("loginCount", 1), User.class);
        log.info("Login: {} ({})", user.getUsername(), user.getRole());
        return issueToken(user);
    }

    /**
     * Creates a customer account and signs it in. The email is the username, so there is one
     * account per email address.
     */
    public LoginResponse register(RegisterRequest request, String visitor) {
        // Only accounts actually created count, so typing mistakes don't use up the allowance
        if (signupsByVisitor.count(visitor) >= maxSignupsPerHour) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_SUBMISSIONS,
                    "Several accounts were created from this device already. Please try again in an hour.");
        }
        try {
            PhoneNumbers.normalize(request.phone());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }
        LocalDate today = LocalDate.now(clock.withZone(rules.timezone()));
        if (request.dateOfBirth().plusYears(rules.minAge()).isAfter(today) || request.dateOfBirth().getYear() < 1900) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.UNDER_AGE,
                    "You need to be " + rules.minAge() + " or older to create an account.");
        }
        String email = normalize(request.email());
        User user = new User(email, passwordEncoder.encode(request.password()), Role.USER, null);
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(request.phone().trim());
        user.setDateOfBirth(request.dateOfBirth());
        Instant now = clock.instant();
        user.setCreatedAt(now);
        user.setLastLoginAt(now);
        user.setLoginCount(1);
        if (users.findByUsername(email).isPresent()) {
            throw emailTaken();
        }
        try {
            users.save(user);
        } catch (DuplicateKeyException e) {
            throw emailTaken(); // registered at the same moment
        }
        signupsByVisitor.record(visitor);
        log.info("New customer account {}", email);
        return issueToken(user);
    }

    /** A customer changes their own password; other devices are signed out, this one gets a new token. */
    public LoginResponse changePassword(String userId, String currentPassword, String newPassword) {
        User user = customerWithPassword(userId, currentPassword);
        Instant now = clock.instant();
        mongo.updateFirst(Query.query(Criteria.where("_id").is(user.getId())), new Update()
                .set("passwordHash", passwordEncoder.encode(newPassword))
                .set("passwordChangedAt", now), User.class);
        log.info("Customer {} changed their password", user.getUsername());
        return issueToken(user);
    }

    /**
     * A customer changes their name, phone or email. The email is the username, so a new one must be
     * free, and the answer is a new sign-in: the old one names the old email (other devices sign in again).
     */
    public LoginResponse updateDetails(String userId, UpdateAccountRequest request) {
        User user = users.findById(userId).filter(u -> u.getRole() == Role.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again."));
        try {
            PhoneNumbers.normalize(request.phone());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PHONE, "Please enter a valid phone number");
        }
        String email = normalize(request.email());
        boolean newEmail = !email.equals(user.getUsername());
        if (newEmail && users.findByUsername(email).isPresent()) {
            throw emailTaken();
        }
        user.setName(request.name().trim());
        user.setPhone(request.phone().trim());
        user.setEmail(email);
        user.setUsername(email);
        try {
            users.save(user);
        } catch (DuplicateKeyException e) {
            throw emailTaken(); // taken at the same moment
        }
        if (newEmail) {
            log.info("Customer {} changed their email", user.getId());
        }
        return issueToken(user);
    }

    /**
     * A customer deletes their own account (name, email, phone, password). Their bookings stay for the
     * shop's records but leave the account; an upcoming one still works with its code + phone.
     */
    public void deleteAccount(String userId, String password) {
        User user = customerWithPassword(userId, password);
        mongo.updateMulti(Query.query(Criteria.where("userId").is(user.getId())), new Update().unset("userId"),
                Booking.class);
        users.delete(user);
        log.info("Customer {} deleted their account", user.getUsername());
    }

    private User customerWithPassword(String userId, String password) {
        User user = users.findById(userId).filter(u -> u.getRole() == Role.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 400, not 401: a typo must not sign the customer out
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Your current password is not right.");
        }
        return user;
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "There's already an account with this email. Please sign in instead.");
    }

    private LoginResponse issueToken(User user) {
        Instant now = clock.instant();
        Instant expires = now.plus(tokenLifetime);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("uid", user.getId())
                .claim("role", user.getRole().name());
        if (user.getArtistId() != null) {
            claims.claim("artistId", user.getArtistId());
        }
        if (user.getName() != null) {
            claims.claim("name", user.getName());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new LoginResponse(token, expires,
                new AuthUserDto(user.getUsername(), user.getRole(), user.getArtistId(), user.getName()));
    }

    /** Deactivated logins, and logins of deactivated artists, cannot sign in. */
    private boolean canSignIn(User user) {
        if (!user.isActive()) {
            return false;
        }
        return user.getArtistId() == null || artists.findById(user.getArtistId()).map(b -> b.isActive()).orElse(false);
    }

    /** Usernames (and customer emails) are stored and compared in lower case. */
    public static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    @Scheduled(fixedRate = 15 * 60 * 1000)
    public void purgeExpired() {
        failedByUsername.purgeExpired();
        failedByVisitor.purgeExpired();
        signupsByVisitor.purgeExpired();
    }
}
