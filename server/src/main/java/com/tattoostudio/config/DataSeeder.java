package com.tattoostudio.config;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.model.Work;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.FlashRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.service.AuthService;
import com.tattoostudio.storage.ImageUploads;

/**
 * Fills a new database with the Blade & Brush demo data (three artists, twelve flash designs, a few
 * reviews) and makes sure the admin login exists.
 *
 * The demo data is added only ONCE per database: a marker document in "app_meta" records that it
 * was done. Without it, deleting e.g. all reviews in the admin pages would bring the demo reviews
 * back on the next restart.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String META_COLLECTION = "app_meta";
    private static final String DEMO_DATA_MARKER = "demo-data-seeded";
    /** Set once the sample flash drawings were given to the sample designs (raise the number for new ones). */
    static final String FLASH_PICTURES_MARKER = "flash-pictures-3";

    /**
     * The drawings that ship with the website (client/public/images/flash), by design name, one for
     * each of the twelve sample designs. Adding or changing a drawing here means raising the number in
     * FLASH_PICTURES_MARKER, so databases that already ran this pick it up.
     */
    static final java.util.Map<String, String> FLASH_PICTURES = java.util.Map.ofEntries(
            java.util.Map.entry("Lucky koi", "flash/lucky-koi.jpg"),
            java.util.Map.entry("Koi and wave", "flash/koi-and-wave.jpg"),
            java.util.Map.entry("Small dragon", "flash/small-dragon.jpg"),
            java.util.Map.entry("Tiger head", "flash/tiger-head.jpg"),
            java.util.Map.entry("Hannya mask", "flash/hannya-mask.jpg"),
            java.util.Map.entry("Oni", "flash/oni.jpg"),
            java.util.Map.entry("Kabuto helmet", "flash/kabuto-helmet.jpg"),
            java.util.Map.entry("Katana and smoke", "flash/katana-and-smoke.jpg"),
            java.util.Map.entry("Crane in flight", "flash/crane-in-flight.jpg"),
            java.util.Map.entry("Peony", "flash/peony.jpg"),
            java.util.Map.entry("Cherry blossom branch", "flash/cherry-blossom-branch.jpg"),
            java.util.Map.entry("Small wave", "flash/small-wave.jpg"));
    /**
     * The sample artists' Instagram handles. Blade & Brush is a concept studio, so these are made up
     * (each ends in ".bladeandbrush" so it is unlikely to be someone's real account).
     */
    static final java.util.Map<String, String> INSTAGRAM = java.util.Map.of(
            "Kai", "kai.bladeandbrush",
            "Dario", "dario.bladeandbrush",
            "Nika", "nika.bladeandbrush");
    /** Set once the sample artists were given their handles, so a handle the admin removes stays removed. */
    static final String INSTAGRAM_MARKER = "artist-instagram-1";
    /** Start the server with --reset-demo to go back to the original demo data (e.g. before presenting). */
    static final String RESET_OPTION = "reset-demo";
    static final String DEMO_CUSTOMER_EMAIL = "mira@example.com";

    private final ArtistRepository artists;
    private final FlashRepository flash;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;
    private final ImageUploads images;
    private final String adminUsername;
    private final String adminPassword;
    private final String demoArtistPassword;

    public DataSeeder(ArtistRepository artists, FlashRepository flash, ReviewRepository reviews, UserRepository users,
            MongoTemplate mongo, PasswordEncoder passwordEncoder, ImageUploads images,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password:}") String adminPassword,
            @Value("${app.demo.artist-password:}") String demoArtistPassword) {
        this.artists = artists;
        this.flash = flash;
        this.reviews = reviews;
        this.users = users;
        this.mongo = mongo;
        this.passwordEncoder = passwordEncoder;
        this.images = images;
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
        giveFlashTheirPicturesOnce();
        giveArtistsTheirInstagramOnce();
        ensureAdmin();
        ensureDemoLogins();
    }

    private void seedDemoDataOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(DEMO_DATA_MARKER)), META_COLLECTION)) {
            return;
        }
        if (artists.count() == 0 && flash.count() == 0 && reviews.count() == 0) {
            List<Artist> crew = seedArtists();
            seedFlash(crew);
            seedReviews(crew);
        } else {
            log.info("Existing data found; not adding demo data");
        }
        mongo.save(new Document("_id", DEMO_DATA_MARKER).append("at", new Date()), META_COLLECTION);
    }

    /**
     * Deletes every booking, message, review, login, flash design, portfolio photo and artist (and
     * their uploaded pictures), so the normal start-up fills the database again with the original demo
     * data and logins. Documents are removed rather than collections dropped, so the indexes (e.g. the
     * double-booking guard) stay.
     */
    private void resetDemoData() {
        log.warn("--reset-demo: deleting all bookings, messages, reviews, logins, flash designs, portfolios and artists");
        List<Image> pictures = new ArrayList<>();
        mongo.findAll(Booking.class).forEach(b -> pictures.addAll(b.getPhotos()));
        mongo.findAll(Message.class).forEach(m -> pictures.addAll(m.getPhotos()));
        mongo.findAll(FlashDesign.class).forEach(f -> pictures.add(f.getImage()));
        mongo.findAll(Work.class).forEach(w -> pictures.add(w.getImage()));
        mongo.findAll(Artist.class).forEach(a -> pictures.add(a.getSignaturePiece()));
        for (Class<?> type : List.of(Booking.class, Message.class, Review.class, User.class, FlashDesign.class, Work.class,
                Artist.class)) {
            mongo.remove(new Query(), type);
        }
        mongo.remove(Query.query(Criteria.where("_id").in(DEMO_DATA_MARKER, FLASH_PICTURES_MARKER, INSTAGRAM_MARKER)), META_COLLECTION);
        images.deleteAll(pictures.stream().filter(p -> p != null).toList());
    }

    /**
     * Gives the sample designs their drawings, once per database (per FLASH_PICTURES_MARKER): a fresh
     * one right after the demo data, an existing one on its next start. A design without a picture gets
     * its drawing, and one still showing an older bundled drawing gets the current one; a picture an
     * artist uploaded is never replaced.
     */
    private void giveFlashTheirPicturesOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(FLASH_PICTURES_MARKER)), META_COLLECTION)) {
            return;
        }
        long given = 0;
        for (var entry : FLASH_PICTURES.entrySet()) {
            Image drawing = Image.bundled(entry.getValue());
            Criteria noUpload = new Criteria().orOperator(Criteria.where("image").is(null),
                    Criteria.where("image.id").regex("^" + java.util.regex.Pattern.quote(Image.BUNDLED)));
            given += mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("name").is(entry.getKey()),
                    noUpload, Criteria.where("image.id").ne(drawing.id()))),
                    new Update().set("image", drawing), FlashDesign.class).getModifiedCount();
        }
        mongo.save(new Document("_id", FLASH_PICTURES_MARKER).append("at", new Date()), META_COLLECTION);
        log.info("Gave {} sample flash designs their drawings", given);
    }

    /**
     * Gives the sample artists their Instagram handles, once per database: a fresh one right after the
     * demo data, an existing one on its next start. An artist who already has a handle keeps it.
     */
    private void giveArtistsTheirInstagramOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(INSTAGRAM_MARKER)), META_COLLECTION)) {
            return;
        }
        long given = 0;
        for (var entry : INSTAGRAM.entrySet()) {
            given += mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("name").is(entry.getKey()),
                    Criteria.where("instagram").is(null))),
                    new Update().set("instagram", entry.getValue()), Artist.class).getModifiedCount();
        }
        mongo.save(new Document("_id", INSTAGRAM_MARKER).append("at", new Date()), META_COLLECTION);
        log.info("Gave {} sample artists their Instagram handles", given);
    }

    /** The studio's hours: Tuesday to Sunday, 11:00 to 23:00; closed on Monday. */
    private static final List<WorkingHours> STUDIO_HOURS = List.of(
            new WorkingHours(TUESDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)),
            new WorkingHours(WEDNESDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)),
            new WorkingHours(THURSDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)),
            new WorkingHours(FRIDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)),
            new WorkingHours(SATURDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)),
            new WorkingHours(SUNDAY, LocalTime.of(11, 0), LocalTime.of(23, 0)));

    private List<Artist> seedArtists() {
        List<Artist> saved = artists.saveAll(List.of(
                artist("Kai", "Bold traditional colour", "Kai tattoos in the bold traditional Japanese style: heavy "
                        + "outlines, solid colour, and backgrounds of wind and water. Most of Kai's work is large: sleeves, "
                        + "backs and chest pieces.", 90, 550, 1),
                artist("Dario", "Black and grey, ink wash", "Dario works only in black and grey: masks, demons and "
                        + "warriors, shaded like ink on paper.", 80, 500, 2),
                artist("Nika", "Fine line", "Nika works with the finest needles: flowers, cranes and waves in thin black "
                        + "lines with small touches of red. A good choice for a first tattoo.", 70, 420, 3)));
        log.info("Seeded artists");
        return saved;
    }

    private static Artist artist(String name, String style, String bio, int hourly, int day, int sortOrder) {
        Artist artist = new Artist(name, name.toLowerCase(), style, bio, BigDecimal.valueOf(hourly), BigDecimal.valueOf(day),
                sortOrder);
        artist.setWeeklyHours(STUDIO_HOURS);
        return artist;
    }

    /** The twelve sample designs from the design brief (their drawings: giveFlashTheirPicturesOnce). */
    private void seedFlash(List<Artist> crew) {
        String kai = crew.get(0).getId();
        String dario = crew.get(1).getId();
        String nika = crew.get(2).getId();
        List<FlashDesign> designs = List.of(
                design(kai, "Lucky koi", 6, 8, 90, 140, 1),
                design(kai, "Koi and wave", 8, 12, 150, 220, 2),
                design(kai, "Small dragon", 6, 12, 120, 180, 3),
                design(kai, "Tiger head", 10, 10, 180, 260, 4),
                design(dario, "Hannya mask", 8, 10, 150, 200, 1),
                design(dario, "Oni", 7, 9, 120, 170, 2),
                design(dario, "Kabuto helmet", 8, 8, 120, 170, 3),
                design(dario, "Katana and smoke", 4, 16, 90, 130, 4),
                design(nika, "Crane in flight", 6, 8, 60, 90, 1),
                design(nika, "Peony", 6, 6, 60, 90, 2),
                design(nika, "Cherry blossom branch", 4, 14, 90, 110, 3),
                design(nika, "Small wave", 5, 5, 60, 80, 4));
        flash.saveAll(designs);
        log.info("Seeded flash designs");
    }

    private static FlashDesign design(String artistId, String name, int width, int height, int minutes, int price,
            int sortOrder) {
        FlashDesign d = new FlashDesign();
        d.setArtistId(artistId);
        d.setName(name);
        d.setWidthCm(width);
        d.setHeightCm(height);
        d.setDurationMinutes(minutes);
        d.setPrice(BigDecimal.valueOf(price));
        d.setSortOrder(sortOrder);
        d.setCreatedAt(Instant.now());
        return d;
    }

    private void seedReviews(List<Artist> crew) {
        Instant now = Instant.now();
        reviews.saveAll(List.of(
                new Review(crew.get(1).getId(), "Elena", 5, "Careful, clear and beautifully done.", true, now.minus(3, ChronoUnit.DAYS)),
                new Review(crew.get(2).getId(), "Sofia", 4, "A calm studio and excellent line work.", true, now.minus(9, ChronoUnit.DAYS)),
                new Review(crew.get(0).getId(), "Petar", 5, "The colour healed bright and solid.", true, now.minus(20, ChronoUnit.DAYS))));
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
     * For the demo: gives every artist without a login one, named after their first name (kai, dario,
     * nika), all with app.demo.artist-password, plus a demo customer account (mira@example.com, same
     * password). Skipped when that is not set. Logins that already exist (e.g. reset by the admin)
     * are left alone.
     */
    private void ensureDemoLogins() {
        if (demoArtistPassword.isBlank()) {
            return;
        }
        if (demoArtistPassword.length() < 8) {
            log.warn("app.demo.artist-password is shorter than 8 characters; no demo logins created");
            return;
        }
        log.warn("Demo logins are enabled (app.demo.artist-password). Remove that setting before real use.");
        for (Artist artist : artists.findAll()) {
            String username = AuthService.normalize(artist.getName().split(" ")[0]);
            if (users.findByArtistId(artist.getId()).isPresent() || users.findByUsername(username).isPresent()) {
                continue;
            }
            users.save(new User(username, passwordEncoder.encode(demoArtistPassword), Role.ARTIST, artist.getId()));
            log.info("Created demo login '{}' for {}", username, artist.getName());
        }
        // And one demo customer account, so "Sign in" / "My account" can be shown without signing up
        if (users.findByUsername(DEMO_CUSTOMER_EMAIL).isEmpty()) {
            User customer = new User(DEMO_CUSTOMER_EMAIL, passwordEncoder.encode(demoArtistPassword), Role.USER, null);
            customer.setName("Mira Petrova");
            customer.setEmail(DEMO_CUSTOMER_EMAIL);
            customer.setPhone("+359 88 123 4567");
            customer.setDateOfBirth(LocalDate.of(1998, 3, 12));
            customer.setCreatedAt(Instant.now());
            users.save(customer);
            log.info("Created demo customer account '{}'", DEMO_CUSTOMER_EMAIL);
        }
    }
}
