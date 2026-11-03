package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.model.ServiceItem;
import com.tattoostudio.support.ApiTest;
import com.jayway.jsonpath.JsonPath;

/** The admin API (/api/admin/**): bookings, stats, services, artists, logins, reviews and messages. */
class AdminApiTest extends ApiTest {

    private String admin;
    private Set<String> seededServices;
    private Set<String> seededArtists;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        admin = adminBearer();
        seededServices = services.findAll().stream().map(ServiceItem::getId).collect(Collectors.toSet());
        seededArtists = artists.findAll().stream().map(Artist::getId).collect(Collectors.toSet());
    }

    /** Puts the demo data back the way the other tests expect it. */
    @AfterEach
    void restoreDemoData() {
        services.findAll().forEach(s -> {
            if (!seededServices.contains(s.getId())) {
                services.delete(s);
            } else if (!s.isActive()) {
                s.setActive(true);
                services.save(s);
            }
        });
        artists.findAll().forEach(b -> {
            if (!seededArtists.contains(b.getId())) {
                users.findByArtistId(b.getId()).ifPresent(users::delete);
                artists.delete(b);
            } else if (!b.isActive()) {
                b.setActive(true);
                artists.save(b);
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
                {"name":"%s","handle":"@new","specialty":"Fades","bio":"New in town.","photoUrl":null,"accent":"cyan",
                 "weeklyHours":[{"day":"TUESDAY","start":"10:00","end":"12:00"}],"exceptions":[]%s}
                """.formatted(name, extra);
    }

    // ---- Bookings and stats ----

    @Test
    void adminSeesAndManagesEveryonesBookings() throws Exception {
        booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        Booking sols = booking("Sol", TUESDAY, "11:00", BookingStatus.BOOKED);

        call(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        call(get("/api/admin/bookings").param("artistId", artist("Sol").getId()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].artistName").value("Sol Reyes"));

        send(patch("/api/admin/bookings/" + sols.getId()), "{\"status\":\"CANCELLED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void statsCountTheWeekAndRevenueOnlyFromDoneBookings() throws Exception {
        booking("Nia", TUESDAY, "10:00", BookingStatus.DONE);       // 28
        booking("Nia", TUESDAY, "11:00", BookingStatus.DONE);       // 28
        booking("Nia", TUESDAY, "12:00", BookingStatus.NO_SHOW);
        booking("Sol", TUESDAY, "10:00", BookingStatus.DONE);       // 28
        booking("Sol", TUESDAY, "11:00", BookingStatus.CANCELLED);
        booking("Sol", TUESDAY, "12:00", BookingStatus.BOOKED);
        booking("Sol", TUESDAY.plusDays(7), "12:00", BookingStatus.DONE); // next week

        call(get("/api/admin/stats")) // default: this week
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(MONDAY.toString()))
                .andExpect(jsonPath("$.to").value(MONDAY.plusDays(6).toString()))
                .andExpect(jsonPath("$.bookings").value(5))
                .andExpect(jsonPath("$.done").value(3))
                .andExpect(jsonPath("$.noShows").value(1))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.revenue").value(84.0))
                .andExpect(jsonPath("$.busiestArtist.name").value("Nia Banks"))
                .andExpect(jsonPath("$.busiestArtist.bookings").value(3))
                .andExpect(jsonPath("$.perArtist.length()").value(6))
                .andExpect(jsonPath("$.perArtist[?(@.name == 'Sol Reyes')].revenue").value(28.0));

        call(get("/api/admin/stats").param("from", MONDAY.toString()).param("to", MONDAY.plusDays(13).toString()))
                .andExpect(jsonPath("$.bookings").value(6))
                .andExpect(jsonPath("$.revenue").value(112.0));
        call(get("/api/admin/stats").param("from", TUESDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest());
    }

    // ---- Services ----

    @Test
    void servicesCanBeAddedEditedAndHidden() throws Exception {
        String created = send(post("/api/admin/services"),
                "{\"name\":\"Kids Cut\",\"description\":\"Under 12.\",\"price\":15.5,\"label\":\"MD 06\",\"palette\":\"blue\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(6))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/services")).andExpect(jsonPath("$[5].name").value("Kids Cut"));

        send(put("/api/admin/services/" + id),
                "{\"name\":\"Kids Cut\",\"description\":\"Under 12.\",\"price\":17,\"label\":\"MD 06\",\"palette\":\"lime\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(17))
                .andExpect(jsonPath("$.palette").value("lime"));

        // Hidden from the booking page (PUT active: false), still in the admin list
        send(put("/api/admin/services/" + id),
                "{\"name\":\"Kids Cut\",\"price\":17,\"palette\":\"lime\",\"active\":false}")
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/services")).andExpect(jsonPath("$.length()").value(5));
        call(get("/api/admin/services"))
                .andExpect(jsonPath("$.items.length()").value(6))
                .andExpect(jsonPath("$.items[5].active").value(false));

        // Shown again
        send(put("/api/admin/services/" + id),
                "{\"name\":\"Kids Cut\",\"price\":17,\"palette\":\"lime\",\"active\":true}")
                .andExpect(jsonPath("$.active").value(true));

        // PATCH changes only the shown / hidden flag
        send(patch("/api/admin/services/" + id), "{\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.price").value(17))
                .andExpect(jsonPath("$.palette").value("lime"));
        mvc.perform(get("/api/services")).andExpect(jsonPath("$.length()").value(5));
        send(patch("/api/admin/services/" + id), "{\"active\":true}").andExpect(jsonPath("$.active").value(true));
        send(patch("/api/admin/services/" + id), "{}").andExpect(status().isBadRequest());

        // Deleted for good
        call(delete("/api/admin/services/" + id)).andExpect(status().isNoContent());
        call(get("/api/admin/services")).andExpect(jsonPath("$.items.length()").value(5));
    }

    @Test
    void serviceWithUpcomingBookingsCannotBeDeletedButPastOnesKeepTheirName() throws Exception {
        String id = JsonPath.read(send(post("/api/admin/services"),
                "{\"name\":\"Test Shave\",\"price\":12,\"palette\":\"mint\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        booking.setServiceId(id);
        booking.setServiceName("Test Shave");
        bookings.save(booking);

        call(delete("/api/admin/services/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Test Shave has 1 upcoming booking. Hide it instead, or cancel them first."));
        send(patch("/api/admin/bookings/" + booking.getId()), "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
        call(delete("/api/admin/services/" + id)).andExpect(status().isNoContent());
        call(get("/api/admin/bookings")).andExpect(jsonPath("$.items[0].serviceName").value("Test Shave"));
    }

    @Test
    void invalidServicesAreRejected() throws Exception {
        send(post("/api/admin/services"), "{\"name\":\"\",\"price\":-1,\"palette\":\"Blue!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists())
                .andExpect(jsonPath("$.errors.palette").exists());
        call(delete("/api/admin/services/nope")).andExpect(status().isNotFound());
    }

    // ---- Artists and logins ----

    @Test
    void newArtistGetsALoginAndCanBeBooked() throws Exception {
        String created = send(post("/api/admin/artists"), artistJson("Kai Stone", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(7))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mvc.perform(get("/api/artists/" + id)).andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY"));

        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"Test-Kai\",\"password\":\"kai-password\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test-kai"));
        login("test-kai", "kai-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.artistId").value(id));
        assertThat(users.findByUsername("test-kai").orElseThrow().getCreatedAt()).isNotNull();
        call(get("/api/admin/artists")).andExpect(jsonPath("$.items[6].username").value("test-kai"));

        // Reset: the old password stops working
        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"test-kai\",\"password\":\"new-password\"}")
                .andExpect(status().isCreated());
        login("test-kai", "kai-password").andExpect(status().isUnauthorized());
        login("test-kai", "new-password").andExpect(status().isOk());
        assertThat(users.findAll().stream().filter(u -> id.equals(u.getArtistId())).count()).isEqualTo(1);
    }

    @Test
    void aArtistCanHaveOnlyOneLogin() {
        createArtistLogin("test-sol", "Sol");
        assertThatThrownBy(() -> createArtistLogin("test-sol-2", "Sol"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void resettingAPasswordSignsOutOldSessions() throws Exception {
        String oldSession = artistBearer("Cruz");
        mvc.perform(get("/api/staff/bookings").header("Authorization", oldSession)).andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(1));
        send(post("/api/admin/artists/" + artist("Cruz").getId() + "/login"),
                "{\"username\":\"test-cruz\",\"password\":\"brand-new-password\"}")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/staff/bookings").header("Authorization", oldSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String newSession = bearer("test-cruz", "brand-new-password");
        mvc.perform(get("/api/staff/bookings").header("Authorization", newSession)).andExpect(status().isOk());
    }

    @Test
    void resettingAArtistsLoginKeepsItBlocked() throws Exception {
        createArtistLogin("test-melo", "Melo");
        String melo = artist("Melo").getId();
        String id = users.findByUsername("test-melo").orElseThrow().getId();
        send(put("/api/admin/users/" + id), "{\"role\":\"ARTIST\",\"active\":false,\"artistId\":\"%s\"}".formatted(melo))
                .andExpect(jsonPath("$.active").value(false));

        send(post("/api/admin/artists/" + melo + "/login"), "{\"username\":\"test-melo\",\"password\":\"melo-new-password\"}")
                .andExpect(status().isCreated());
        login("test-melo", "melo-new-password").andExpect(status().isUnauthorized());
        assertThat(users.findByUsername("test-melo").orElseThrow().isActive()).isFalse();
    }

    @Test
    void pickersGetEveryArtistByNameWithTheirLogin() throws Exception {
        createArtistLogin("test-rook", "Rook");
        call(get("/api/admin/artists/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("Cruz Lee"))
                .andExpect(jsonPath("$[5].name").value("Sol Reyes"))
                .andExpect(jsonPath("$[4].username").value("test-rook"))
                .andExpect(jsonPath("$[4].active").value(true))
                .andExpect(jsonPath("$[0].weeklyHours").doesNotExist());
        mvc.perform(get("/api/admin/artists/options").header("Authorization", artistBearer("Sol")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theActiveSwitchKeepsHoursTheArtistSavedMeanwhile() throws Exception {
        Artist sol = artist("Sol");
        String solToken = artistBearer("Sol");
        try {
            // Sol changes their own hours after the admin opened the artist list
            mvc.perform(put("/api/staff/schedule").header("Authorization", solToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"weeklyHours\":[{\"day\":\"WEDNESDAY\",\"start\":\"10:00\",\"end\":\"18:00\"}],\"exceptions\":[]}"))
                    .andExpect(status().isOk());

            send(patch("/api/admin/artists/" + sol.getId()), "{\"active\":false}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false))
                    .andExpect(jsonPath("$.weeklyHours.length()").value(1));
            send(patch("/api/admin/artists/" + sol.getId()), "{\"active\":true}")
                    .andExpect(jsonPath("$.active").value(true));
            assertThat(artist("Sol").getWeeklyHours()).hasSize(1);

            // Deactivating with an upcoming booking is refused, as with PUT
            booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
            send(patch("/api/admin/artists/" + artist("Nia").getId()), "{\"active\":false}")
                    .andExpect(status().isConflict());
        } finally {
            artists.save(sol); // the seeded hours back
        }
    }

    @Test
    void loginsNeedAFreeUsernameAndALongEnoughPassword() throws Exception {
        String nia = artist("Nia").getId();
        send(post("/api/admin/artists/" + nia + "/login"), "{\"username\":\"admin\",\"password\":\"long-enough\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        send(post("/api/admin/artists/" + nia + "/login"), "{\"username\":\"test-nia\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        send(post("/api/admin/artists/" + nia + "/login"), "{\"username\":\"no spaces\",\"password\":\"long-enough\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void artistWithUpcomingBookingsCannotBeDeactivated() throws Exception {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        String niaToken = artistBearer("Nia");
        String nia = artist("Nia").getId();

        Artist original = artist("Nia");
        String deactivate = artistJson("Nia Banks", ",\"active\":false");
        try {
            send(put("/api/admin/artists/" + nia), deactivate)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Nia Banks still has 1 upcoming booking. Cancel them first."));
            // Deleting is refused too: she has bookings in the history
            call(delete("/api/admin/artists/" + nia)).andExpect(status().isConflict());

            send(patch("/api/admin/bookings/" + booking.getId()), "{\"status\":\"CANCELLED\"}").andExpect(status().isOk());
            send(put("/api/admin/artists/" + nia), deactivate).andExpect(status().isOk());
        } finally {
            Artist restored = artist("Nia");
            original.setActive(restored.isActive());
            artists.save(original); // seeded profile and hours back (the clean-up re-activates her)
        }

        // Off the booking page, and their login and existing token stop working
        mvc.perform(get("/api/artists")).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/artists/" + nia)).andExpect(status().isNotFound());
        login("test-nia", ARTIST_PASSWORD).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/bookings").header("Authorization", niaToken)).andExpect(status().isForbidden());
        call(get("/api/admin/artists")).andExpect(jsonPath("$.items[3].active").value(false));
    }

    @Test
    void adminSeesBookingsOutsideAArtistsHours() throws Exception {
        booking("Sol", TUESDAY, "12:00", BookingStatus.BOOKED);
        call(get("/api/admin/artists"))
                .andExpect(jsonPath("$.items[?(@.name == 'Sol Reyes')].conflicts.length()").value(0));

        Artist sol = artist("Sol");
        String json = artistJson("Sol Reyes", "").replace("\"exceptions\":[]",
                "\"exceptions\":[{\"date\":\"%s\",\"closed\":true}]".formatted(TUESDAY));
        try {
            send(put("/api/admin/artists/" + sol.getId()), json)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.conflicts.length()").value(1))
                    .andExpect(jsonPath("$.conflicts[0].time").value("12:00"));
        } finally {
            artists.save(sol); // restore the seeded profile and hours
        }
    }

    @Test
    void artistWithoutBookingsCanBeDeletedWithTheirLogin() throws Exception {
        String id = JsonPath.read(send(post("/api/admin/artists"), artistJson("Kai Stone", ""))
                .andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/api/admin/artists/" + id + "/login"), "{\"username\":\"test-kai\",\"password\":\"kai-password\"}")
                .andExpect(status().isCreated());
        call(delete("/api/admin/artists/" + id)).andExpect(status().isNoContent());
        call(get("/api/admin/artists")).andExpect(jsonPath("$.totalItems").value(6));
        login("test-kai", "kai-password").andExpect(status().isUnauthorized());
    }

    @Test
    void adminCorrectsAndDeletesBookings() throws Exception {
        Booking booking = booking("Sol", TUESDAY, "11:00", BookingStatus.BOOKED);
        send(put("/api/admin/bookings/" + booking.getId()), """
                {"customerName":"Ivan Petrov Jr.","phone":"0899 111 222","email":"","notes":"Bring the kid"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ivan Petrov Jr."))
                .andExpect(jsonPath("$.phone").value("+359899111222"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.notes").value("Bring the kid"))
                .andExpect(jsonPath("$.status").value("BOOKED"));
        send(put("/api/admin/bookings/" + booking.getId()), """
                {"customerName":"","phone":"12","email":"x"}""").andExpect(status().isBadRequest());

        Booking wiped = booking("Sol", TUESDAY, "12:00", BookingStatus.DONE);
        wiped.setAnonymized(true);
        bookings.save(wiped);
        send(put("/api/admin/bookings/" + wiped.getId()), """
                {"customerName":"Back again","phone":"0899 111 222"}""").andExpect(status().isConflict());

        call(delete("/api/admin/bookings/" + booking.getId())).andExpect(status().isNoContent());
        call(get("/api/admin/bookings")).andExpect(jsonPath("$.totalItems").value(1));
        call(delete("/api/admin/bookings/" + booking.getId())).andExpect(status().isNotFound());
        // An artist can't delete or edit bookings
        mvc.perform(delete("/api/admin/bookings/" + wiped.getId()).header("Authorization", artistBearer("Sol")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidArtistsAreRejected() throws Exception {
        send(post("/api/admin/artists"), """
                {"name":"X","accent":"pink","photoUrl":"javascript:alert(1)","weeklyHours":[],"exceptions":[]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accent").exists())
                .andExpect(jsonPath("$.errors.photoUrl").exists());
        send(post("/api/admin/artists"), artistJson("Kai Stone", "").replace("\"12:00\"", "\"09:00\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void publicArtistListShowsUpcomingDaysOff() throws Exception {
        Artist nia = artist("Nia");
        String json = artistJson("Nia Banks", "").replace("\"exceptions\":[]",
                "\"exceptions\":[{\"date\":\"%s\",\"closed\":true,\"note\":\"Holiday\"}]".formatted(TUESDAY));
        send(put("/api/admin/artists/" + nia.getId()), json)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptions[0].date").value(TUESDAY.toString()));
        try {
            mvc.perform(get("/api/artists/" + nia.getId()))
                    .andExpect(jsonPath("$.exceptions[0].date").value(TUESDAY.toString()))
                    .andExpect(jsonPath("$.exceptions[0].closed").value(true));
            clock.advance(Duration.ofDays(2)); // the day off is in the past now
            mvc.perform(get("/api/artists/" + nia.getId())).andExpect(jsonPath("$.exceptions.length()").value(0));
        } finally {
            artists.save(nia); // restore the seeded profile and hours
        }
    }

    // ---- Reviews and messages ----

    @Test
    void reviewsAreModerated() throws Exception {
        Review pending = reviews.save(new Review("Test Pending", 5, "Great.", false, NOW));
        reviews.save(new Review("Test Approved", 4, "Good.", true, NOW));

        call(get("/api/admin/reviews").param("status", "pending"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Test Pending"))
                .andExpect(jsonPath("$.items[0].approved").value(false));
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
        Review review = reviews.save(new Review("Test Writer", 2, "Call me on 0888 123 456", false, clock.instant()));
        send(put("/api/admin/reviews/" + review.getId()), """
                {"name":"Test Writer","rating":4,"text":"Great cut.","approved":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.text").value("Great cut."))
                .andExpect(jsonPath("$.approved").value(true));
        send(put("/api/admin/reviews/" + review.getId()), """
                {"name":"","rating":9,"text":"","approved":true}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    @Test
    void messagesCanBeReadAndDeleted() throws Exception {
        Message older = message("Ana", NOW.minus(Duration.ofDays(1)));
        message("Boris", NOW);

        call(get("/api/admin/messages"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Boris")) // newest first
                .andExpect(jsonPath("$.items[1].read").value(false));
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
        m.setText("Do you do kids cuts?");
        m.setCreatedAt(createdAt);
        return messages.save(m);
    }
}
