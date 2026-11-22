package com.tattoostudio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.model.Image;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.storage.ImageUploads;
import com.tattoostudio.support.IntegrationTest;

class DataSeederTest extends IntegrationTest {

    @Autowired
    DataSeeder seeder;
    @Autowired
    UserRepository users;
    @Autowired
    MongoTemplate mongo;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ImageUploads images;

    @Test
    void theDemoStudioHasThreeArtistsAndTwelveFlashDesigns() {
        assertThat(artists.findAll()).extracting(Artist::getName).containsExactlyInAnyOrder("Kai", "Dario", "Nika");
        assertThat(artist("Kai").getSlug()).isEqualTo("kai");
        assertThat(artist("Kai").getWeeklyHours()).hasSize(6); // closed on Monday
        assertThat(artist("Kai").getBreakMinutes()).isEqualTo(30);
        assertThat(flash.findAll()).hasSize(12);
        assertThat(flash("Tiger head").getPrice()).isEqualByComparingTo("260");
        assertThat(flash("Tiger head").getDurationMinutes()).isEqualTo(180);
        // Each comes with a drawing that ships with the website
        assertThat(flash("Tiger head").getImage()).isEqualTo(DataSeeder.flashDrawing("flash/tiger-head.jpg"));
        assertThat(flash("Tiger head").getImage().thumbUrl()).isEqualTo("/images/flash/small/tiger-head.webp");
        assertThat(flash("Tiger head").getImage().url()).isEqualTo("/images/flash/tiger-head.jpg");
        assertThat(DataSeeder.FLASH_PICTURES).hasSize(12);
        assertThat(flash.findAll()).allMatch(f -> f.getImage() != null);
        // Each artist's scroll shows a picture that ships with the website
        assertThat(artist("Kai").getSignaturePiece()).isEqualTo(
                Image.bundled("artists/kai-dragon-colour.webp", "artists/small/kai-dragon-colour.webp"));
        assertThat(artist("Dario").getSignaturePiece().thumbUrl()).isEqualTo("/images/artists/small/dario-mask.webp");
        assertThat(artist("Nika").getSignaturePiece().url()).isEqualTo("/images/artists/nika-crane.webp");
    }

    @Test
    void drawingsReachDesignsSeededBeforeThemButNeverReplaceAnUpload() {
        Image uploaded = new Image("flash/own.png", "/uploads/flash/own.png", "/uploads/flash/own.png");
        FlashDesign peony = flash("Peony");
        FlashDesign koi = flash("Lucky koi");
        Image peonyBefore = peony.getImage();
        Image koiBefore = koi.getImage();
        try {
            // A database seeded before the drawings existed: no pictures, except one an artist uploaded
            peony.setImage(null);
            flash.save(peony);
            koi.setImage(uploaded);
            flash.save(koi);
            mongo.remove(Query.query(Criteria.where("_id").is(DataSeeder.FLASH_PICTURES_MARKER)), "app_meta");

            seeder.run(null);
            seeder.run(null); // the next start changes nothing

            assertThat(flash("Peony").getImage()).isEqualTo(DataSeeder.flashDrawing("flash/peony.jpg"));
            assertThat(flash("Lucky koi").getImage()).isEqualTo(uploaded);

            // A drawing renamed since (older bundled path) is brought up to date on the next marker
            FlashDesign stale = flash("Peony");
            stale.setImage(Image.bundled("flash/old-peony.jpg"));
            flash.save(stale);
            mongo.remove(Query.query(Criteria.where("_id").is(DataSeeder.FLASH_PICTURES_MARKER)), "app_meta");
            seeder.run(null);
            assertThat(flash("Peony").getImage()).isEqualTo(DataSeeder.flashDrawing("flash/peony.jpg"));
            assertThat(flash("Lucky koi").getImage()).isEqualTo(uploaded);
        } finally {
            FlashDesign p = flash("Peony");
            p.setImage(peonyBefore);
            flash.save(p);
            FlashDesign k = flash("Lucky koi");
            k.setImage(koiBefore);
            flash.save(k);
        }
    }

    @Test
    void samplePortfolioPhotosAreAddedOnceToTheirArtists() {
        // (each test starts with empty portfolios) a database that has not had the sample photos yet
        mongo.remove(Query.query(Criteria.where("_id").is(DataSeeder.SAMPLE_WORKS_MARKER)), "app_meta");
        seeder.run(null);
        seeder.run(null); // the next start adds nothing
        assertThat(works.count()).isEqualTo(DataSeeder.SAMPLE_WORKS.size()).isEqualTo(13);
        assertThat(works.findByArtistIdOrderByCreatedAtDesc(artist("Kai").getId()))
                .extracting(com.tattoostudio.model.Work::getCaption)
                .containsExactly("Koi half sleeve", "Kabuki warrior, upper arm", "Phoenix back piece",
                        "Koi and chrysanthemum sleeve");
        assertThat(works.findAll()).allMatch(w -> w.getImage().isBundled() && !w.getCaption().isBlank());
        // Each with a small WebP copy for the photo grids
        assertThat(works.findAll()).allMatch(w -> w.getImage().thumbUrl().startsWith("/images/work/small/")
                && w.getImage().thumbUrl().endsWith(".webp"));
    }

    @Test
    void deletedDemoDataDoesNotComeBackOnRestart() {
        List<Review> saved = reviews.findAll();
        try {
            reviews.deleteAll();
            seeder.run(null); // what happens on the next server start
            assertThat(reviews.count()).isZero();
        } finally {
            reviews.saveAll(saved);
        }
    }

    @Test
    void demoPasswordGivesEveryArtistALoginOnce() {
        DataSeeder withDemoLogins = new DataSeeder(artists, flash, reviews, users, mongo, passwordEncoder, images,
                "admin", "test-admin-password", "demo-password");
        try {
            // An artist who already has a login (e.g. reset by the admin) keeps it
            users.save(new User("test-nika", passwordEncoder.encode("own-password"), Role.ARTIST, artist("Nika").getId()));
            withDemoLogins.run(null);
            withDemoLogins.run(null); // restarting does not create duplicates
            List<User> artistLogins = users.findAll().stream().filter(u -> u.getRole() == Role.ARTIST).toList();
            assertThat(artistLogins).extracting(User::getUsername).containsExactlyInAnyOrder("kai", "dario", "test-nika");
            User kai = users.findByUsername("kai").orElseThrow();
            assertThat(kai.getArtistId()).isEqualTo(artist("Kai").getId());
            assertThat(passwordEncoder.matches("demo-password", kai.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("own-password",
                    users.findByUsername("test-nika").orElseThrow().getPasswordHash())).isTrue();
            User customer = users.findByUsername(DataSeeder.DEMO_CUSTOMER_EMAIL).orElseThrow();
            assertThat(customer.getRole()).isEqualTo(Role.USER);
            assertThat(customer.getDateOfBirth()).isNotNull();
        } finally {
            users.deleteAll(users.findAll().stream()
                    .filter(u -> u.getRole() == Role.ARTIST || DataSeeder.DEMO_CUSTOMER_EMAIL.equals(u.getUsername())).toList());
        }
    }

    @Test
    void resetDemoBringsBackTheOriginalData() {
        Message message = new Message();
        message.setName("Leftover");
        message.setContact("0888123456");
        message.setText("From a test run");
        message.setCreatedAt(NOW);
        messages.save(message);
        reviews.save(new Review(artist("Kai").getId(), "Leftover", 5, "From a test run", true, NOW));
        flash.delete(flash("Peony"));
        Artist nika = artist("Nika");
        nika.setActive(false);
        artists.save(nika);

        seeder.run(new DefaultApplicationArguments("--reset-demo"));

        assertThat(messages.count()).isZero();
        assertThat(bookings.count()).isZero();
        assertThat(reviews.findAll()).isNotEmpty().allMatch(Review::isApproved).noneMatch(r -> r.getName().equals("Leftover"));
        assertThat(flash.findAll()).extracting(FlashDesign::getName).contains("Peony").hasSize(12);
        assertThat(artists.findAll()).hasSize(3).allMatch(Artist::isActive);
        // Every flash design belongs to one of the new artists
        assertThat(flash.findAll()).allMatch(f -> artists.findById(f.getArtistId()).isPresent());
        assertThat(users.findByUsername("admin")).isPresent();
    }

    @Test
    void resetRefusesToRunWithoutAnAdminPassword() {
        DataSeeder withoutAdminPassword = new DataSeeder(artists, flash, reviews, users, mongo, passwordEncoder, images,
                "admin", "", "");
        long flashBefore = flash.count();
        assertThatThrownBy(() -> withoutAdminPassword.run(new DefaultApplicationArguments("--reset-demo")))
                .hasMessageContaining("app.admin.password");
        assertThat(flash.count()).isEqualTo(flashBefore); // nothing was deleted
    }

    @Test
    void noDemoLoginsWithoutADemoPassword() {
        seeder.run(null); // the test profile leaves app.demo.artist-password empty
        assertThat(users.findAll()).noneMatch(u -> u.getRole() == Role.ARTIST);
    }
}
