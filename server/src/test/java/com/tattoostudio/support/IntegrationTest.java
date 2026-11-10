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
import com.tattoostudio.model.FlashDesign;
import com.tattoostudio.repository.ArtistRepository;
import com.tattoostudio.repository.BookingRepository;
import com.tattoostudio.repository.FlashRepository;
import com.tattoostudio.repository.MessageRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.repository.WorkRepository;

/**
 * Base for tests that need the full app and the test database (tattoo_studio_test).
 * Time is frozen at Monday 08:00 studio time in 2030, so "tomorrow" is always a Tuesday.
 * The studio is closed on Mondays; Tuesday to Sunday Kai, Dario and Nika work 11:00–23:00 with a
 * 30-minute break after each client (see DataSeeder).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    protected static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Sofia");
    protected static final LocalDate MONDAY = LocalDate.of(2030, 1, 1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    protected static final LocalDate TUESDAY = MONDAY.plusDays(1);
    protected static final Instant NOW = at(MONDAY, "08:00");
    /** Names of flash designs tests add start with this, so they are removed again. */
    protected static final String TEST_FLASH = "Test flash";

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
    protected FlashRepository flash;
    @Autowired
    protected WorkRepository works;
    @Autowired
    protected ReviewRepository reviews;
    @Autowired
    protected MessageRepository messages;

    @BeforeEach
    void resetState() {
        clock.set(NOW);
        bookings.deleteAll();
        messages.deleteAll();
        works.deleteAll();
        reviews.deleteAll(reviews.findAll().stream().filter(r -> !r.isApproved() || r.getBookingId() != null).toList());
        flash.deleteAll(flash.findAll().stream().filter(f -> f.getName().startsWith(TEST_FLASH)).toList());
        flash.findAll().forEach(f -> {
            if (!f.isAvailable()) {
                f.setAvailable(true);
                flash.save(f);
            }
        });
        // Undo day exceptions, breaks and deactivations a previous test made
        artists.findAll().forEach(a -> {
            if (!a.getExceptions().isEmpty() || a.getBreakMinutes() != 30 || !a.isActive() || a.getSignaturePiece() != null) {
                a.getExceptions().clear();
                a.setBreakMinutes(30);
                a.setActive(true);
                a.setSignaturePiece(null);
                artists.save(a);
            }
        });
    }

    protected static Instant at(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(SHOP_ZONE).toInstant();
    }

    /** Finds a seeded artist by name, e.g. "Nika". */
    protected Artist artist(String name) {
        return artists.findAll().stream()
                .filter(a -> a.getName().split(" ")[0].equals(name))
                .findFirst().orElseThrow();
    }

    /** Finds a seeded flash design by name, e.g. "Small dragon" (Kai, 2 hours, €180). */
    protected FlashDesign flash(String name) {
        return flash.findAll().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
    }
}
