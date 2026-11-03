package com.tattoostudio.config;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.model.User;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.repository.ServiceItemRepository;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.service.AuthService;

/**
 * Fills a new database with the Tattoo Studio demo data and makes sure the admin login exists.
 *
 * The demo data is added only ONCE per database: a marker document in "app_meta" records
 * that it was done. Without it, deleting e.g. all reviews in the dashboard would bring the
 * demo reviews back on the next restart.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String META_COLLECTION = "app_meta";
    private static final String DEMO_DATA_MARKER = "demo-data-seeded";
    /** Start the server with --reset-demo to go back to the original demo data (e.g. before presenting). */
    static final String RESET_OPTION = "reset-demo";

    private final ServiceItemRepository services;
    private final ArtistRepository artists;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String demoArtistPassword;

    public DataSeeder(ServiceItemRepository services, ArtistRepository artists, ReviewRepository reviews,
            UserRepository users, MongoTemplate mongo, PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.demo.artist-password:}") String demoArtistPassword) {
        this.services = services;
        this.artists = artists;
        this.reviews = reviews;
        this.users = users;
        this.mongo = mongo;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoArtistPassword = demoArtistPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args != null && args.containsOption(RESET_OPTION)) {
            // The reset also deletes the admin login, so make sure it can be created again first
            if (adminPassword.isBlank()) {
                throw new IllegalStateException("--reset-demo needs app.admin.password, otherwise nobody could sign in afterwards");
            }
            resetDemoData();
        }
        seedDemoDataOnce();
        ensureAdmin();
        ensureDemoArtistLogins();
    }

    private void seedDemoDataOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(DEMO_DATA_MARKER)), META_COLLECTION)) {
            return;
        }
        if (services.count() == 0 && artists.count() == 0 && reviews.count() == 0) {
            seedServices();
            seedArtists();
            seedReviews();
        } else {
            log.info("Existing data found; not adding demo data");
        }
        mongo.save(new Document("_id", DEMO_DATA_MARKER).append("at", new Date()), META_COLLECTION);
    }

    /**
     * Deletes every booking, message, review, login, service and artist, so the normal start-up
     * fills the database again with the original demo data and logins. Documents are removed
     * rather than collections dropped, so the indexes (e.g. the double-booking guard) stay.
     */
    private void resetDemoData() {
        log.warn("--reset-demo: deleting all bookings, messages, reviews, logins, services and artists");
        for (Class<?> type : List.of(Booking.class, Message.class, Review.class, User.class, ServiceItem.class, Artist.class)) {
            mongo.remove(new Query(), type);
        }
        mongo.remove(Query.query(Criteria.where("_id").is(DEMO_DATA_MARKER)), META_COLLECTION);
    }

    private void seedServices() {
        services.saveAll(List.of(
                new ServiceItem("Street Cut", "Consultation, tailored cut and finish.", new BigDecimal("28.00"), "MD 01", "blue", 1),
                new ServiceItem("Skin Fade", "Skin-tight fade with a crisp shape-up.", new BigDecimal("34.00"), "MD 02", "sunset", 2),
                new ServiceItem("Line-Up", "Clean edges, neck and detail work.", new BigDecimal("18.00"), "MD 03", "lime", 3),
                new ServiceItem("Beard Sculpt", "Shape, hot towel and razor finish.", new BigDecimal("22.00"), "MD 04", "mint", 4),
                new ServiceItem("Cut + Beard", "The full reset from hairline to jawline.", new BigDecimal("46.00"), "MD 05", "violet", 5)));
        log.info("Seeded services");
    }

    /** Every artist works the shop hours except for one personal day off. */
    private void seedArtists() {
        artists.saveAll(List.of(
                artist("Jax Monroe", "@jaxcuts", "Precision fades", "Sharp gradients and immaculate detail work.", "red", 1, TUESDAY),
                artist("Melo King", "@melomade", "Texture specialist", "Movement, texture and effortless shape.", "orange", 2, WEDNESDAY),
                artist("Sol Reyes", "@solshapes", "Modern classics", "Classic technique with a new-school silhouette.", "yellow", 3, THURSDAY),
                artist("Nia Banks", "@niabladed", "Creative cuts", "Graphic lines, bold shapes and clean finishes.", "green", 4, FRIDAY),
                artist("Cruz Lee", "@cruzcontrol", "Scissor work", "Technical scissor cuts built to grow out right.", "cyan", 5, SATURDAY),
                artist("Rook Davis", "@rookfade", "Fade architect", "Low, mid or high—every blend lands clean.", "purple", 6, SUNDAY)));
        log.info("Seeded artists");
    }

    private void seedReviews() {
        Instant now = Instant.now();
        reviews.saveAll(List.of(
                new Review("Mira K.", 5, "The cleanest fade I’ve had in Plovdiv.", true, now.minus(3, ChronoUnit.DAYS)),
                new Review("Daniel P.", 5, "Great energy, sharp details, right on time.", true, now.minus(9, ChronoUnit.DAYS)),
                new Review("Theo R.", 4, "Easy booking and a brilliant cut.", true, now.minus(20, ChronoUnit.DAYS))));
        log.info("Seeded reviews");
    }

    /** Creates the admin login if there is none. Refuses to start without a chosen password. */
    private void ensureAdmin() {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminPassword.isBlank()) {
            throw new IllegalStateException("No admin account exists yet. Set app.admin.password in "
                    + "src/main/resources/secrets.properties (see secrets.properties.example) and restart.");
        }
        users.save(new User(AuthService.normalize(adminUsername), passwordEncoder.encode(adminPassword), Role.ADMIN, null));
        log.info("Created admin login '{}'", adminUsername);
    }

    /**
     * For the demo: gives every artist without a login one, named after their first name
     * (jax, melo, ...), all with app.demo.artist-password, plus a demo customer account
     * (alex@example.com, same password). Skipped when that is not set.
     * Logins that already exist (e.g. reset by the admin) are left alone.
     */
    private void ensureDemoArtistLogins() {
        if (demoArtistPassword.isBlank()) {
            return;
        }
        if (demoArtistPassword.length() < 8) {
            log.warn("app.demo.artist-password is shorter than 8 characters; no demo artist logins created");
            return;
        }
        log.warn("Demo artist logins are enabled (app.demo.artist-password). Remove that setting before real use.");
        for (Artist artist : artists.findAll()) {
            String username = AuthService.normalize(artist.getName().split(" ")[0]);
            if (users.findByArtistId(artist.getId()).isPresent() || users.findByUsername(username).isPresent()) {
                continue;
            }
            users.save(new User(username, passwordEncoder.encode(demoArtistPassword), Role.ARTIST, artist.getId()));
            log.info("Created demo login '{}' for {}", username, artist.getName());
        }
        // And one demo customer account, so "Sign in" / "My bookings" can be shown without signing up
        if (users.findByUsername(DEMO_CUSTOMER_EMAIL).isEmpty()) {
            User customer = new User(DEMO_CUSTOMER_EMAIL, passwordEncoder.encode(demoArtistPassword), Role.USER, null);
            customer.setName("Alex Morgan");
            customer.setEmail(DEMO_CUSTOMER_EMAIL);
            customer.setPhone("0888 123 456");
            customer.setCreatedAt(Instant.now());
            users.save(customer);
            log.info("Created demo customer account '{}'", DEMO_CUSTOMER_EMAIL);
        }
    }

    static final String DEMO_CUSTOMER_EMAIL = "alex@example.com";

    /** Shop opening hours: Monday closed, Tue–Fri 10–20, Saturday 9–19, Sunday 10–17. */
    private static final List<WorkingHours> SHOP_HOURS = List.of(
            new WorkingHours(TUESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0)),
            new WorkingHours(WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(20, 0)),
            new WorkingHours(THURSDAY, LocalTime.of(10, 0), LocalTime.of(20, 0)),
            new WorkingHours(FRIDAY, LocalTime.of(10, 0), LocalTime.of(20, 0)),
            new WorkingHours(SATURDAY, LocalTime.of(9, 0), LocalTime.of(19, 0)),
            new WorkingHours(SUNDAY, LocalTime.of(10, 0), LocalTime.of(17, 0)));

    private static Artist artist(String name, String handle, String specialty, String bio, String accent,
            int sortOrder, DayOfWeek dayOff) {
        Artist artist = new Artist(name, handle, specialty, bio, accent, sortOrder);
        artist.setWeeklyHours(SHOP_HOURS.stream().filter(h -> h.day() != dayOff).toList());
        return artist;
    }

}
