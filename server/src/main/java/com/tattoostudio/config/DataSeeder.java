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
    static final String FLASH_PICTURES_MARKER = "flash-pictures-4";

    /** A sample flash drawing with its small copy (flash/small/, WebP) for the flash cards. */
    public static Image flashDrawing(String path) {
        return Image.bundled(path, path.replace("flash/", "flash/small/").replace(".jpg", ".webp"));
    }

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
    /**
     * The sample artists' signature pieces that ship with the website (client/public/images/artists),
     * by artist name. Adding or changing one means raising the number in SIGNATURE_PICTURES_MARKER.
     */
    public static final java.util.Map<String, String> SIGNATURE_PICTURES = java.util.Map.of(
            "Kai", "artists/kai-dragon-colour.webp",
            "Dario", "artists/dario-mask.webp",
            "Nika", "artists/nika-crane.webp");
    /** Set once the sample artists were given their signature pieces (raise the number for new ones). */
    static final String SIGNATURE_PICTURES_MARKER = "signature-pictures-4";

    /**
     * The picture an artist's scroll ships with (null for an artist without one): the full picture, and
     * a small copy (artists/small/, about 60 KB instead of 400) for the small scrolls and cards.
     */
    public static Image shippedSignaturePiece(String artistName) {
        String path = SIGNATURE_PICTURES.get(artistName);
        return path == null ? null : Image.bundled(path, path.replace("artists/", "artists/small/"));
    }

    /** A sample portfolio photo with its small copy (work/small/, WebP) for the photo grids. */
    static Image samplePhoto(String path) {
        return Image.bundled(path, path.replace("work/", "work/small/").replace(".jpg", ".webp"));
    }
    /**
     * The sample portfolio photos that ship with the website (client/public/images/work), newest first:
     * artist name, file, caption. Collected for this concept studio (see the Image credits page).
     * Adding or changing one means raising the number in SAMPLE_WORKS_MARKER.
     */
    public static final List<String[]> SAMPLE_WORKS = List.of(
            new String[] { "Nika", "work/nika-dragon-sleeve.jpg", "Fine-line dragon sleeve" },
            new String[] { "Kai", "work/kai-koi-half-sleeve.jpg", "Koi half sleeve" },
            new String[] { "Dario", "work/dario-dragon-hannya-sleeve.jpg", "Dragon and hannya sleeve" },
            new String[] { "Nika", "work/nika-koi-lotus-forearm.jpg", "Koi and lotus forearm" },
            new String[] { "Kai", "work/kai-kabuki-warrior.jpg", "Kabuki warrior, upper arm" },
            new String[] { "Dario", "work/dario-oni-snake-back.jpg", "Oni and snake back piece" },
            new String[] { "Kai", "work/kai-phoenix-back.jpg", "Phoenix back piece" },
            new String[] { "Nika", "work/nika-peony-fan-calf.jpg", "Peony fan on the calf" },
            new String[] { "Dario", "work/dario-hannya-snake-leg.jpg", "Hannya and snake leg sleeve" },
            new String[] { "Kai", "work/kai-koi-chrysanthemum-sleeve.jpg", "Koi and chrysanthemum sleeve" },
            new String[] { "Nika", "work/nika-koi-pagoda-forearm.jpg", "Koi and pagoda forearm" },
            new String[] { "Nika", "work/nika-tiger-red-sun-sleeve.jpg", "Tiger and red sun sleeve" },
            new String[] { "Dario", "work/dario-manga-panel-sleeve.jpg", "Manga panel sleeve" });
    /** Set once the sample portfolio photos were added (raise the number for new ones). */
    static final String SAMPLE_WORKS_MARKER = "sample-works-3";
    /**
     * The sample reviews, three per artist (artist name, reviewer, stars, text, days ago). They read as
     * real reviews do: the piece, the placement, the healing.
     */
    record SampleReview(String artist, String name, int rating, String text, int daysAgo) {
    }

    static final List<SampleReview> SAMPLE_REVIEWS = List.of(
            new SampleReview("Kai", "Petar", 5, "Kai did a koi and wave half sleeve on my forearm over two sessions. The colour healed bright and solid, and the outlines are as crisp as the drawing. He explained every step of the aftercare.", 4),
            new SampleReview("Kai", "Martin", 5, "I booked the Small dragon flash and was done in two hours. Kai placed it perfectly on my calf and checked the stencil with me twice before starting.", 16),
            new SampleReview("Kai", "Desislava", 4, "A big back piece in progress with Kai, three sessions so far. The work is excellent; the only reason for four stars is that his next free day was a month away.", 33),
            new SampleReview("Dario", "Elena", 5, "Dario tattooed a hannya mask on my shoulder in black and grey. The shading is so soft it looks painted, and it healed without a single patch. Careful, clear and beautifully done.", 2),
            new SampleReview("Dario", "Georgi", 5, "My first black and grey piece, an oni on the upper arm. Dario talked me through the size and placement, and the session felt calm the whole time.", 12),
            new SampleReview("Dario", "Radoslav", 5, "I came in with a rough idea for a samurai, and at the consultation Dario turned it into something far better. Worth every euro.", 27),
            new SampleReview("Nika", "Sofia", 4, "A calm studio and excellent line work. Nika's crane on my ribs is delicate, but every line is clean. It took a little longer than planned, which I'd rather have than a rushed job.", 8),
            new SampleReview("Nika", "Ivana", 5, "My first tattoo, a small cherry blossom branch on the wrist. Nika was patient with all my questions, and the red touches still look fresh after two months.", 20),
            new SampleReview("Nika", "Kalina", 5, "A fine line peony on my thigh. Nika suggested making it slightly bigger so the lines would age well, and she was right.", 40));
    /** Set once the longer sample reviews were added (raise the number when they change). */
    static final String SAMPLE_REVIEWS_MARKER = "sample-reviews-1";
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
        giveArtistsTheirSignaturePiecesOnce();
        addSampleWorksOnce();
        addSampleReviewsOnce();
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
        mongo.remove(Query.query(Criteria.where("_id").in(DEMO_DATA_MARKER, FLASH_PICTURES_MARKER, INSTAGRAM_MARKER,
                SIGNATURE_PICTURES_MARKER, SAMPLE_WORKS_MARKER, SAMPLE_REVIEWS_MARKER)), META_COLLECTION);
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
            Image drawing = flashDrawing(entry.getValue());
            Criteria noUpload = new Criteria().orOperator(Criteria.where("image").is(null),
                    Criteria.where("image.id").regex("^" + java.util.regex.Pattern.quote(Image.BUNDLED)));
            given += mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("name").is(entry.getKey()),
                    noUpload, new Criteria().orOperator(Criteria.where("image.id").ne(drawing.id()),
                            Criteria.where("image.thumbUrl").ne(drawing.thumbUrl())))),
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

    /**
     * Gives the sample artists their signature pieces, once per database (per
     * SIGNATURE_PICTURES_MARKER), like giveFlashTheirPicturesOnce: an artist without a piece, or still
     * showing an older bundled one, gets the current one; a piece an artist uploaded is never replaced.
     */
    private void giveArtistsTheirSignaturePiecesOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(SIGNATURE_PICTURES_MARKER)), META_COLLECTION)) {
            return;
        }
        long given = 0;
        for (var entry : SIGNATURE_PICTURES.entrySet()) {
            Image piece = shippedSignaturePiece(entry.getKey());
            Criteria noUpload = new Criteria().orOperator(Criteria.where("signaturePiece").is(null),
                    Criteria.where("signaturePiece.id").regex("^" + java.util.regex.Pattern.quote(Image.BUNDLED)));
            // A different shipped picture, or the same one without its small copy yet
            Criteria outdated = new Criteria().orOperator(Criteria.where("signaturePiece.id").ne(piece.id()),
                    Criteria.where("signaturePiece.thumbUrl").ne(piece.thumbUrl()));
            given += mongo.updateFirst(Query.query(new Criteria().andOperator(Criteria.where("name").is(entry.getKey()),
                    noUpload, outdated)),
                    new Update().set("signaturePiece", piece), Artist.class).getModifiedCount();
        }
        mongo.save(new Document("_id", SIGNATURE_PICTURES_MARKER).append("at", new Date()), META_COLLECTION);
        log.info("Gave {} sample artists their signature pieces", given);
    }

    /**
     * Adds the sample portfolio photos, once per database (per SAMPLE_WORKS_MARKER), newest first a day
     * apart. A photo already in an artist's portfolio is not added twice (it only gets its small copy
     * if it has none yet), and one the admin or an artist removed stays removed until the marker is raised.
     */
    private void addSampleWorksOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(SAMPLE_WORKS_MARKER)), META_COLLECTION)) {
            return;
        }
        java.time.Instant newest = java.time.Instant.now();
        int added = 0;
        for (int i = 0; i < SAMPLE_WORKS.size(); i++) {
            String[] sample = SAMPLE_WORKS.get(i);
            Image photo = samplePhoto(sample[1]);
            Artist artist = mongo.findOne(Query.query(Criteria.where("name").is(sample[0])), Artist.class);
            if (artist == null) {
                continue;
            }
            if (mongo.exists(Query.query(Criteria.where("image.id").is(photo.id())), Work.class)) {
                mongo.updateMulti(Query.query(new Criteria().andOperator(Criteria.where("image.id").is(photo.id()),
                        Criteria.where("image.thumbUrl").ne(photo.thumbUrl()))), new Update().set("image", photo), Work.class);
                continue;
            }
            Work work = new Work();
            work.setArtistId(artist.getId());
            work.setImage(photo);
            work.setCaption(sample[2]);
            work.setCreatedAt(newest.minus(java.time.Duration.ofDays(i)));
            mongo.save(work);
            added++;
        }
        mongo.save(new Document("_id", SAMPLE_WORKS_MARKER).append("at", new Date()), META_COLLECTION);
        log.info("Added {} sample portfolio photos", added);
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

    /**
     * Adds the sample reviews, once per database (per SAMPLE_REVIEWS_MARKER): a fresh one right after
     * the demo data, an existing one on its next start. A reviewer who already has a review for that
     * artist keeps theirs, but one still showing the first short sample text gets the longer one.
     */
    private void addSampleReviewsOnce() {
        if (mongo.exists(Query.query(Criteria.where("_id").is(SAMPLE_REVIEWS_MARKER)), META_COLLECTION)) {
            return;
        }
        List<String> firstSamples = List.of("Careful, clear and beautifully done.", "A calm studio and excellent line work.",
                "The colour healed bright and solid.");
        Instant now = Instant.now();
        int added = 0;
        for (SampleReview sample : SAMPLE_REVIEWS) {
            Artist artist = mongo.findOne(Query.query(Criteria.where("name").is(sample.artist())), Artist.class);
            if (artist == null) {
                continue;
            }
            Query same = Query.query(new Criteria().andOperator(Criteria.where("artistId").is(artist.getId()),
                    Criteria.where("name").is(sample.name()), Criteria.where("bookingId").is(null)));
            if (mongo.exists(same, Review.class)) {
                mongo.updateMulti(same.addCriteria(Criteria.where("text").in(firstSamples)),
                        new Update().set("text", sample.text()).set("rating", sample.rating())
                                .set("createdAt", now.minus(sample.daysAgo(), ChronoUnit.DAYS)), Review.class);
                continue;
            }
            reviews.save(new Review(artist.getId(), sample.name(), sample.rating(), sample.text(), true,
                    now.minus(sample.daysAgo(), ChronoUnit.DAYS)));
            added++;
        }
        mongo.save(new Document("_id", SAMPLE_REVIEWS_MARKER).append("at", new Date()), META_COLLECTION);
        log.info("Added {} sample reviews", added);
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
