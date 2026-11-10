package com.tattoostudio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.FlashDesign;
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
