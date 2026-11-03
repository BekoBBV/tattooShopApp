package com.tattoostudio.support;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.MessageRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.repository.ServiceItemRepository;

/**
 * Base for tests that need the full app and the test database (tattoo_studio_test).
 * Time is frozen at Monday 08:00 shop time in 2030, so "tomorrow" is always a Tuesday.
 * On Tuesdays the shop is open 10:00–20:00 and every artist works except Jax (his day off),
 * so Melo, Sol, Nia, Cruz and Rook have one-hour slots from 10:00 to 19:00 (see DataSeeder).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    protected static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Sofia");
    protected static final LocalDate MONDAY = LocalDate.of(2030, 1, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    protected static final LocalDate TUESDAY = MONDAY.plusDays(1);
    protected static final Instant NOW = at(MONDAY, "08:00");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected BookingRepository bookings;
    @Autowired
    protected ArtistRepository artists;
    @Autowired
    protected ServiceItemRepository services;
    @Autowired
    protected ReviewRepository reviews;
    @Autowired
    protected MessageRepository messages;

    @BeforeEach
    void resetState() {
        clock.set(NOW);
        bookings.deleteAll();
        messages.deleteAll();
        reviews.deleteAll(reviews.findAll().stream().filter(r -> !r.isApproved()).toList());
        // Undo any day exceptions a previous test added
        artists.findAll().forEach(b -> {
            if (!b.getExceptions().isEmpty()) {
                b.getExceptions().clear();
                artists.save(b);
            }
        });
    }

    protected static Instant at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(SHOP_ZONE).toInstant();
    }

    /** Finds a seeded artist by first name, e.g. "Nia". */
    protected Artist artist(String firstName) {
        return artists.findAll().stream()
                .filter(b -> b.getName().split(" ")[0].equals(firstName))
                .findFirst().orElseThrow();
    }

    protected ServiceItem service(String name) {
        return services.findAll().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
}
