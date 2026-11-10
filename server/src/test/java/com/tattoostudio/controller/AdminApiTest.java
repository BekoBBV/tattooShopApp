package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositMethod;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.support.ApiTest;

/** The admin API (/api/admin/**): bookings, deposits, stats, flash, artists, logins, reviews and messages. */
class AdminApiTest extends ApiTest {

    private String admin;
    private Set<String> seededArtists;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        admin = adminBearer();
        seededArtists = artists.findAll().stream().map(Artist::getId).collect(Collectors.toSet());
    }

    /** Puts the demo data back the way the other tests expect it. */
    @AfterEach
    void restoreDemoData() {
        artists.findAll().forEach(a -> {
            if (!seededArtists.contains(a.getId())) {
                users.findByArtistId(a.getId()).ifPresent(users::delete);
                artists.delete(a);
            }
        });
        reviews.deleteAll(reviews.findAll().stream().filter(r -> r.getName().startsWith("Test ")).toList());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", admin));
    }

    private static String artistJson(String name, String extra) {
        return """
                {"name":"%s","style":"Irezumi","bio":"New in the studio.","hourlyRate":85,"dayRate":500,
                 "weeklyHours":[{"day":"TUESDAY","start":"11:00","end":"15:00"}],"exceptions":[]%s}
                """.formatted(name, extra);
    }

    /** Restores a seeded artist's profile, hours and state after a test changed them. */
    private void restore(Artist original) {
        artists.save(original);
    }

    // ---- Bookings, deposits and stats ----

    @Test
    void adminSeesAndManagesEveryonesBookings() throws Exception {
        booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        Booking darios = booking("Dario", TUESDAY, "12:00", 30, BookingType.CONSULTATION, BookingStatus.BOOKED);

        call(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        call(get("/api/admin/bookings").param("artistId", artist("Dario").getId()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].artistName").value("Dario"));
        call(get("/api/admin/bookings").param("type", "SESSION")).andExpect(jsonPath("$.items.length()").value(1));

        send(patch("/api/admin/bookings/" + darios.getId()), "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void depositsCanBePaidInCashAndFiltered() throws Exception {
        String held = JsonPath.read(bookFlash("Small dragon", TUESDAY, "14:00"), "$.code");
        Booking session = booking("Dario", TUESDAY, "11:00", BookingStatus.BOOKED);
        session.setDepositAmount(new BigDecimal("100"));
        session.setDepositStatus(DepositStatus.WAITING);
        session.setDepositDueAt(NOW.minus(Duration.ofHours(1))); // overdue
        bookings.save(session);

        call(get("/api/admin/bookings").param("depositStatus", "WAITING"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.code == '%s')].depositOverdue".formatted(session.getCode())).value(true))
                .andExpect(jsonPath("$.items[?(@.code == '%s')].depositOverdue".formatted(held)).value(false));

        Booking flashBooking = bookings.findByCode(held).orElseThrow();
        call(post("/api/admin/bookings/" + flashBooking.getId() + "/deposit-cash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.deposit.status").value("PAID"))
                .andExpect(jsonPath("$.deposit.method").value("CASH"));
        call(post("/api/admin/bookings/" + flashBooking.getId() + "/deposit-cash"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_PAY"));
        call(get("/api/admin/bookings").param("depositMethod", DepositMethod.CASH.name()))
                .andExpect(jsonPath("$.items.length()").value(1));

        // A booking without a deposit has nothing to pay
        Booking consultation = booking("Nika", TUESDAY, "11:00", 30, BookingType.CONSULTATION, BookingStatus.BOOKED);
        call(post("/api/admin/bookings/" + consultation.getId() + "/deposit-cash")).andExpect(status().isConflict());
    }

    @Test
    void statsCountTheWeekAndRevenueFromDoneBookingsAndKeptDeposits() throws Exception {
        booking("Nika", TUESDAY, "11:00", BookingStatus.DONE);       // 100
        booking("Nika", TUESDAY, "13:00", BookingStatus.DONE);       // 100
        booking("Nika", TUESDAY, "15:00", BookingStatus.NO_SHOW);
        booking("Dario", TUESDAY, "11:00", BookingStatus.DONE);      // 100
        Booking late = booking("Dario", TUESDAY, "13:00", BookingStatus.CANCELLED);
        late.setDepositAmount(new BigDecimal("30"));
        late.setDepositStatus(DepositStatus.KEPT);                   // 30
        bookings.save(late);
        booking("Dario", TUESDAY, "15:00", BookingStatus.BOOKED);
        booking("Dario", TUESDAY.plusDays(7), "15:00", BookingStatus.DONE); // next week

        call(get("/api/admin/stats")) // default: this week
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(MONDAY.toString()))
                .andExpect(jsonPath("$.to").value(MONDAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.bookings").value(5))
                .andExpect(jsonPath("$.done").value(3))
                .andExpect(jsonPath("$.noShows").value(1))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.revenue").value(330.0))
                .andExpect(jsonPath("$.busiestArtist.name").value("Nika"))
                .andExpect(jsonPath("$.busiestArtist.bookings").value(3))
                .andExpect(jsonPath("$.perArtist.length()").value(3))
                // A filter returns a list, which is compared strictly: 130 is sent as a whole number
                .andExpect(jsonPath("$.perArtist[?(@.name == 'Dario')].revenue").value(org.hamcrest.Matchers.contains(130)));

        call(get("/api/admin/stats").param("from", MONDAY.toString()).param("to", MONDAY.plusDays(13).toString()))
                .andExpect(jsonPath("$.bookings").value(6))
                .andExpect(jsonPath("$.revenue").value(430.0));
        call(get("/api/admin/stats").param("from", TUESDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCorrectsAndDeletesBookings() throws Exception {
        Booking booking = booking("Dario", TUESDAY, "11:00", BookingStatus.BOOKED);
        send(put("/api/admin/bookings/" + booking.getId()), """
                {"customerName":"Ivan Petrov Jr.","phone":"0899 111 222","email":"","notes":"Bring a snack"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ivan Petrov Jr."))
                .andExpect(jsonPath("$.phone").value("+359899111222"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.notes").value("Bring a snack"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
        send(put("/api/admin/bookings/" + booking.getId()), """
                {"customerName":"","phone":"12","email":"x"}""").andExpect(status().isBadRequest());

        Booking wiped = booking("Dario", TUESDAY, "14:00", BookingStatus.DONE);
        wiped.setAnonymized(true);
        bookings.save(wiped);
        send(put("/api/admin/bookings/" + wiped.getId()), """
                {"customerName":"Back again","phone":"0899 111 222"}""").andExpect(status().isConflict());

        call(delete("/api/admin/bookings/" + booking.getId())).andExpect(status().isNoContent());
        call(get("/api/admin/bookings")).andExpect(jsonPath("$.totalItems").value(1));
        call(delete("/api/admin/bookings/" + booking.getId())).andExpect(status().isNotFound());
        // An artist can't delete or edit bookings
        mvc.perform(delete("/api/admin/bookings/" + wiped.getId()).header("Authorization", artistBearer("Dario")))
                .andExpect(status().isForbidden());
    }

    // ---- Flash catalogue ----

    @Test
    void theFlashCatalogueShowsEveryArtistsDesigns() throws Exception {
        var dragon = flash("Small dragon");
        dragon.setAvailable(false);
        flash.save(dragon);
        call(get("/api/admin/flash").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.items[?(@.name == 'Small dragon')].state").value("HIDDEN"));
        call(get("/api/admin/flash").param("artistId", artist("Dario").getId()))
                .andExpect(jsonPath("$.totalItems").value(4));
        call(get("/api/admin/flash").param("available", "false")).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/admin/flash").header("Authorization", artistBearer("Dario"))).andExpect(status().isForbidden());
    }

    @Test
    void adminAddsEditsAndDeletesDesignsForAnyArtist() throws Exception {
        MockMultipartFile json = new MockMultipartFile("flash", "", MediaType.APPLICATION_JSON_VALUE, """
                {"artistId":"%s","name":"%s oni","widthCm":9,"heightCm":13,"durationMinutes":150,"price":210}
                """.formatted(artist("Dario").getId(), TEST_FLASH).getBytes());
        String id = JsonPath.read(mvc.perform(multipart("/api/admin/flash").file(json).header("Authorization", admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artistName").value("Dario"))
                .andExpect(jsonPath("$.size").value("LARGE"))
                .andExpect(jsonPath("$.image").doesNotExist())
                .andReturn().getResponse().getContentAsString(), "$.id");

        send(put("/api/admin/flash/" + id), """
                {"name":"%s oni","widthCm":9,"heightCm":12,"durationMinutes":150,"price":200,"available":false}
                """.formatted(TEST_FLASH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value("MEDIUM"))
                .andExpect(jsonPath("$.price").value(200))
                .andExpect(jsonPath("$.state").value("HIDDEN"));
        mvc.perform(multipart("/api/admin/flash/" + id + "/image").file(photo("image", "oni.png"))
                        .with(r -> {
                            r.setMethod("PUT");
                            return r;
                        })
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.image.url").exists());
        call(delete("/api/admin/flash/" + id)).andExpect(status().isNoContent());

        MockMultipartFile noArtist = new MockMultipartFile("flash", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"%s x\",\"widthCm\":9,\"heightCm\":13,\"durationMinutes\":150,\"price\":210}".formatted(TEST_FLASH)
                        .getBytes());
        mvc.perform(multipart("/api/admin/flash").file(noArtist).header("Authorization", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));
    }

    // ---- Artists and logins ----

    @Test
    void newArtistGetsAPageAddressALoginAndCanBeBooked() throws Exception {
        String created = send(post("/api/admin/artists"), artistJson("Ana María", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("ana-maria"))
                .andExpect(jsonPath("$.sortOrder").value(4))
                .andExpect(jsonPath("$.breakMinutes").value(30))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/artists/ana-maria"))
                .andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY"))
                .andExpect(jsonPath("$.hourlyRate").value(85));
        mvc.perform(get("/api/availability").param("artistId", id).param("from", TUESDAY.toString()).param("days", "1"))
                .andExpect(jsonPath("$.days[0].slots[0].time").value("11:00"));
        // The page address is unique
        send(post("/api/admin/artists"), artistJson("Ana Maria", "")).andExpect(status().isConflict());

        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"Test-Ana\",\"password\":\"ana-password\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test-ana"));
        login("test-ana", "ana-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.artistId").value(id));
        call(get("/api/admin/artists")).andExpect(jsonPath("$.items[3].username").value("test-ana"));

        // Reset: the old password stops working
        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"test-ana\",\"password\":\"new-password\"}")
                .andExpect(status().isCreated());
        login("test-ana", "ana-password").andExpect(status().isUnauthorized());
        login("test-ana", "new-password").andExpect(status().isOk());
        assertThat(users.findAll().stream().filter(u -> id.equals(u.getArtistId())).count()).isEqualTo(1);
    }

    @Test
    void anArtistCanHaveOnlyOneLogin() {
        createArtistLogin("test-dario", "Dario");
        assertThatThrownBy(() -> createArtistLogin("test-dario-2", "Dario"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void resettingAPasswordSignsOutOldSessions() throws Exception {
        String oldSession = artistBearer("Kai");
        mvc.perform(get("/api/staff/bookings").header("Authorization", oldSession)).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(1));
        send(post("/api/admin/artists/" + artist("Kai").getId() + "/login"),
                "{\"username\":\"test-kai\",\"password\":\"brand-new-password\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/staff/bookings").header("Authorization", oldSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String newSession = bearer("test-kai", "brand-new-password");
        mvc.perform(get("/api/staff/bookings").header("Authorization", newSession)).andExpect(status().isOk());
    }

    @Test
    void resettingAnArtistsLoginKeepsItBlocked() throws Exception {
        createArtistLogin("test-nika", "Nika");
        String nika = artist("Nika").getId();
        String id = users.findByUsername("test-nika").orElseThrow().getId();
        send(put("/api/admin/users/" + id), "{\"role\":\"ARTIST\",\"active\":false,\"artistId\":\"%s\"}".formatted(nika))
                .andExpect(jsonPath("$.active").value(false));

        send(post("/api/admin/artists/" + nika + "/login"), "{\"username\":\"test-nika\",\"password\":\"nika-new-password\"}")
                .andExpect(status().isCreated());
        login("test-nika", "nika-new-password").andExpect(status().isUnauthorized());
        assertThat(users.findByUsername("test-nika").orElseThrow().isActive()).isFalse();
    }

    @Test
    void pickersGetEveryArtistByNameWithTheirLogin() throws Exception {
        createArtistLogin("test-kai", "Kai");
        call(get("/api/admin/artists/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Dario"))
                .andExpect(jsonPath("$[1].username").value("test-kai"))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[0].weeklyHours").doesNotExist());
        mvc.perform(get("/api/admin/artists/options").header("Authorization", artistBearer("Nika")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theActiveSwitchKeepsHoursTheArtistSavedMeanwhile() throws Exception {
        Artist kai = artist("Kai");
        String kaiToken = artistBearer("Kai");
        try {
            mvc.perform(put("/api/staff/schedule").header("Authorization", kaiToken).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"weeklyHours\":[{\"day\":\"WEDNESDAY\",\"start\":\"11:00\",\"end\":\"18:00\"}],\"exceptions\":[]}"))
                    .andExpect(status().isOk());

            send(patch("/api/admin/artists/" + kai.getId()), "{\"active\":false}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.weeklyHours.length()").value(1));
            // Off the site, and so are their flash designs
            mvc.perform(get("/api/flash")).andExpect(jsonPath("$.length()").value(8));
            send(patch("/api/admin/artists/" + kai.getId()), "{\"active\":true}")
                    .andExpect(jsonPath("$.active").value(true));
            assertThat(artist("Kai").getWeeklyHours()).hasSize(1);

            // Deactivating with an upcoming booking is refused, as with PUT
            booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
            send(patch("/api/admin/artists/" + artist("Nika").getId()), "{\"active\":false}")
                    .andExpect(status().isConflict());
        } finally {
            restore(kai);
        }
    }

    @Test
    void loginsNeedAFreeUsernameAndALongEnoughPassword() throws Exception {
        String nika = artist("Nika").getId();
        send(post("/api/admin/artists/" + nika + "/login"), "{\"username\":\"admin\",\"password\":\"long-enough\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        send(post("/api/admin/artists/" + nika + "/login"), "{\"username\":\"test-nika\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        send(post("/api/admin/artists/" + nika + "/login"), "{\"username\":\"no spaces\",\"password\":\"long-enough\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void artistWithUpcomingBookingsCannotBeDeactivated() throws Exception {
        Booking booking = booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        String nikaToken = artistBearer("Nika");
        Artist original = artist("Nika");
        String nika = original.getId();
        String deactivate = artistJson("Nika", ",\"slug\":\"nika\",\"active\":false");
        try {
            send(put("/api/admin/artists/" + nika), deactivate)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Nika still has 1 upcoming booking. Cancel them first."));
            // Deleting is refused too: she has bookings in the history
            call(delete("/api/admin/artists/" + nika)).andExpect(status().isConflict());

            send(patch("/api/admin/bookings/" + booking.getId()), "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
            send(put("/api/admin/artists/" + nika), deactivate).andExpect(status().isOk());

            // Off the site, and their login and existing token stop working
            mvc.perform(get("/api/artists")).andExpect(jsonPath("$.length()").value(2));
            mvc.perform(get("/api/artists/nika")).andExpect(status().isNotFound());
            login("test-nika", ARTIST_PASSWORD).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/staff/bookings").header("Authorization", nikaToken)).andExpect(status().isForbidden());
            call(get("/api/admin/artists")).andExpect(jsonPath("$.items[2].active").value(false));
        } finally {
            restore(original);
        }
    }

    @Test
    void adminSeesBookingsOutsideAnArtistsHours() throws Exception {
        booking("Dario", TUESDAY, "12:00", 60, BookingType.FLASH, BookingStatus.BOOKED);
        call(get("/api/admin/artists"))
                .andExpect(jsonPath("$.items[?(@.name == 'Dario')].conflicts.length()").value(0));

        Artist dario = artist("Dario");
        String json = artistJson("Dario", ",\"slug\":\"dario\"").replace("\"exceptions\":[]",
                "\"exceptions\":[{\"date\":\"%s\",\"closed\":true}]".formatted(TUESDAY));
        try {
            send(put("/api/admin/artists/" + dario.getId()), json)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conflicts.length()").value(1))
                    .andExpect(jsonPath("$.conflicts[0].time").value("12:00"));
        } finally {
            restore(dario);
        }
    }

    @Test
    void artistWithoutBookingsCanBeDeletedWithTheirLoginAndDesigns() throws Exception {
        String id = JsonPath.read(send(post("/api/admin/artists"), artistJson("Test Artist", ""))
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"test-artist\",\"password\":\"artist-password\"}")
                .andExpect(status().isCreated());
        MockMultipartFile json = new MockMultipartFile("flash", "", MediaType.APPLICATION_JSON_VALUE,
                "{\"artistId\":\"%s\",\"name\":\"%s y\",\"widthCm\":5,\"heightCm\":5,\"durationMinutes\":60,\"price\":80}"
                        .formatted(id, TEST_FLASH).getBytes());
        mvc.perform(multipart("/api/admin/flash").file(json).header("Authorization", admin)).andExpect(status().isCreated());

        call(delete("/api/admin/artists/" + id)).andExpect(status().isNoContent());
        call(get("/api/admin/artists")).andExpect(jsonPath("$.totalItems").value(3));
        assertThat(flash.countByArtistId(id)).isZero();
        login("test-artist", "artist-password").andExpect(status().isUnauthorized());
    }

    @Test
    void invalidArtistsAreRejected() throws Exception {
        send(post("/api/admin/artists"), """
                {"name":"X","slug":"Not A Slug!","hourlyRate":-5,"weeklyHours":[],"exceptions":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slug").exists())
                .andExpect(jsonPath("$.errors.hourlyRate").exists());
        send(post("/api/admin/artists"), artistJson("Test Artist", "").replace("\"15:00\"", "\"09:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(post("/api/admin/artists"), artistJson("Test Artist", ",\"breakMinutes\":20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publicArtistListShowsUpcomingDaysOff() throws Exception {
        Artist nika = artist("Nika");
        String json = artistJson("Nika", ",\"slug\":\"nika\"").replace("\"exceptions\":[]",
                "\"exceptions\":[{\"date\":\"%s\",\"closed\":true,\"note\":\"Tattoo convention, Milan\"}]".formatted(TUESDAY));
        try {
            send(put("/api/admin/artists/" + nika.getId()), json)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exceptions[0].date").value(TUESDAY.toString()));
            mvc.perform(get("/api/artists/nika"))
                    .andExpect(jsonPath("$.exceptions[0].date").value(TUESDAY.toString()))
                    .andExpect(jsonPath("$.exceptions[0].closed").value(true));
            clock.advance(Duration.ofDays(2)); // the day off is in the past now
            mvc.perform(get("/api/artists/nika")).andExpect(jsonPath("$.exceptions.length()").value(0));
        } finally {
            restore(nika);
        }
    }

    @Test
    void signaturePiecesCanBeSetAndRemoved() throws Exception {
        String kai = artist("Kai").getId();
        mvc.perform(multipart("/api/admin/artists/" + kai + "/signature").file(photo("image", "tiger.png"))
                        .with(r -> {
                            r.setMethod("PUT");
                            return r;
                        })
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
        assertThat(artist("Kai").getSignaturePiece()).isNotNull();
        call(delete("/api/admin/artists/" + kai + "/signature")).andExpect(status().isNoContent());
        assertThat(artist("Kai").getSignaturePiece()).isNull();
    }

    // ---- Reviews and messages ----

    @Test
    void reviewsAreModerated() throws Exception {
        Review pending = reviews.save(new Review(artist("Kai").getId(), "Test Pending", 5, "Great.", false, NOW));
        reviews.save(new Review(artist("Nika").getId(), "Test Approved", 4, "Good.", true, NOW));

        call(get("/api/admin/reviews").param("status", "pending"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Test Pending"))
                .andExpect(jsonPath("$.items[0].artistId").value(artist("Kai").getId()))
                .andExpect(jsonPath("$.items[0].approved").value(false));
        call(get("/api/admin/reviews").param("artistId", artist("Nika").getId()))
                .andExpect(jsonPath("$.items.length()").value(2));
        send(patch("/api/admin/reviews/" + pending.getId()), "{\"approved\":true}")
                .andExpect(jsonPath("$.approved").value(true));
        call(get("/api/admin/reviews").param("status", "pending")).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.reviews[?(@.name == 'Test Pending')]").exists());

        call(delete("/api/admin/reviews/" + pending.getId())).andExpect(status().isNoContent());
        assertThat(reviews.findById(pending.getId())).isEmpty();
        call(get("/api/admin/reviews").param("status", "spam")).andExpect(status().isBadRequest());
    }

    @Test
    void adminEditsAReview() throws Exception {
        Review review = reviews.save(new Review(artist("Kai").getId(), "Test Writer", 2, "Call me on 0888 123 456", false,
                clock.instant()));
        send(put("/api/admin/reviews/" + review.getId()), """
                {"name":"Test Writer","rating":4,"text":"Bright colour.","approved":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.text").value("Bright colour."))
                .andExpect(jsonPath("$.approved").value(true));
        send(put("/api/admin/reviews/" + review.getId()), """
                {"name":"","rating":9,"text":"","approved":true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    @Test
    void messagesCanBeHandledAndDeleted() throws Exception {
        Message older = message("Ana", NOW.minus(Duration.ofDays(1)));
        message("Boris", NOW);

        call(get("/api/admin/messages"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Boris")) // newest first
                .andExpect(jsonPath("$.items[1].read").value(false))
                .andExpect(jsonPath("$.items[1].photos.length()").value(0));
        send(patch("/api/admin/messages/" + older.getId()), "{\"read\":true}")
                .andExpect(jsonPath("$.read").value(true));
        call(delete("/api/admin/messages/" + older.getId())).andExpect(status().isNoContent());
        call(get("/api/admin/messages")).andExpect(jsonPath("$.items.length()").value(1));
        call(delete("/api/admin/messages/" + older.getId())).andExpect(status().isNotFound());
    }

    private Message message(String name, java.time.Instant createdAt) {
        Message m = new Message();
        m.setName(name);
        m.setContact("0888123456");
        m.setText("Could this old tattoo be covered?");
        m.setCreatedAt(createdAt);
        return messages.save(m);
    }
}
