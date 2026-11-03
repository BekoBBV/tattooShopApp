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
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;
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
        DataSeeder withDemoLogins = new DataSeeder(services, artists, reviews, users, mongo, passwordEncoder,
                "admin", "test-admin-password", "demo-password");
        try {
            // An artist who already has a login (e.g. reset by the admin) keeps it
            users.save(new User("test-nia", passwordEncoder.encode("own-password"), Role.ARTIST, artist("Nia").getId()));

            withDemoLogins.run(null);
            withDemoLogins.run(null); // restarting does not create duplicates

            List<User> artistLogins = users.findAll().stream().filter(u -> u.getRole() == Role.ARTIST).toList();
            assertThat(artistLogins).extracting(User::getUsername)
                    .containsExactlyInAnyOrder("jax", "melo", "sol", "test-nia", "cruz", "rook");
            User jax = users.findByUsername("jax").orElseThrow();
            assertThat(jax.getArtistId()).isEqualTo(artist("Jax").getId());
            assertThat(passwordEncoder.matches("demo-password", jax.getPasswordHash())).isTrue();
            assertThat(passwordEncoder.matches("own-password",
                    users.findByUsername("test-nia").orElseThrow().getPasswordHash())).isTrue();
        } finally {
            users.deleteAll(users.findAll().stream().filter(u -> u.getRole() == Role.ARTIST).toList());
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
        reviews.save(new Review("Leftover", 5, "From a test run", true, NOW));
        services.delete(service("Line-Up"));
        Artist nia = artist("Nia");
        nia.setActive(false);
        artists.save(nia);

        seeder.run(new DefaultApplicationArguments("--reset-demo"));

        assertThat(messages.count()).isZero();
        assertThat(bookings.count()).isZero();
        assertThat(reviews.findAll()).isNotEmpty().allMatch(Review::isApproved).noneMatch(r -> r.getName().equals("Leftover"));
        assertThat(services.findAll()).extracting(ServiceItem::getName).contains("Line-Up").hasSize(5);
        assertThat(artists.findAll()).hasSize(6).allMatch(Artist::isActive);
        assertThat(users.findByUsername("admin")).isPresent();
    }

    @Test
    void resetRefusesToRunWithoutAnAdminPassword() {
        DataSeeder withoutAdminPassword = new DataSeeder(services, artists, reviews, users, mongo, passwordEncoder,
                "admin", "", "");
        long servicesBefore = services.count();
        assertThatThrownBy(() -> withoutAdminPassword.run(new DefaultApplicationArguments("--reset-demo")))
                .hasMessageContaining("app.admin.password");
        assertThat(services.count()).isEqualTo(servicesBefore); // nothing was deleted
    }

    @Test
    void noDemoLoginsWithoutADemoPassword() {
        seeder.run(null); // the test profile leaves app.demo.artist-password empty
        assertThat(users.findAll()).noneMatch(u -> u.getRole() == Role.ARTIST);
    }
}
