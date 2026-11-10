package com.tattoostudio.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.support.ApiTest;
import com.jayway.jsonpath.JsonPath;

/** The admin's users & rights page: list, add, change roles, block, reset passwords, delete. */
class AdminUserApiTest extends ApiTest {

    private ResultActions send(String auth, MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String create(String admin, String json) throws Exception {
        String body = send(admin, post("/api/admin/users"), json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static final String CUSTOMER = """
            {"role":"USER","name":"Mila Test","email":"test-mila@example.com","phone":"0888 222 333","password":"customer-password"}""";

    @Test
    void adminAddsEveryKindOfLoginAndFindsThem() throws Exception {
        String admin = adminBearer();
        create(admin, CUSTOMER);
        create(admin, """
                {"role":"ARTIST","username":"Test-Kai","artistId":"%s","password":"artist-password"}""".formatted(artist("Kai").getId()));
        create(admin, """
                {"role":"ADMIN","username":"test-boss","password":"boss-password"}""");

        login("test-mila@example.com", "customer-password").andExpect(jsonPath("$.user.role").value("USER"));
        login("test-kai", "artist-password").andExpect(jsonPath("$.user.artistId").value(artist("Kai").getId()));
        login("test-boss", "boss-password").andExpect(jsonPath("$.user.role").value("ADMIN"));

        mvc.perform(get("/api/admin/users").header("Authorization", admin).param("q", "test-").param("sort", "username,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].username").value("test-boss"))
                .andExpect(jsonPath("$.items[1].artistName").value("Kai"))
                .andExpect(jsonPath("$.items[2].name").value("Mila Test"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/admin/users").header("Authorization", admin).param("role", "USER").param("q", "mila"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void promotingACustomerSignsThemOutAndOpensTheAdminPages() throws Exception {
        String admin = adminBearer();
        String id = create(admin, CUSTOMER);
        String old = bearer("test-mila@example.com", "customer-password");
        mvc.perform(get("/api/admin/stats").header("Authorization", old)).andExpect(status().isForbidden());

        clock.advance(Duration.ofSeconds(2)); // tokens count whole seconds
        send(admin, put("/api/admin/users/" + id), """
                {"role":"ADMIN","active":true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/account").header("Authorization", old)).andExpect(status().isUnauthorized());

        clock.advance(Duration.ofSeconds(2));
        String fresh = bearer("test-mila@example.com", "customer-password");
        mvc.perform(get("/api/admin/stats").header("Authorization", fresh)).andExpect(status().isOk());
    }

    @Test
    void movingAArtistLoginToAnotherArtistSignsItOut() throws Exception {
        String admin = adminBearer();
        String id = create(admin, """
                {"role":"ARTIST","username":"test-nika","artistId":"%s","password":"artist-password"}""".formatted(artist("Nika").getId()));
        String old = bearer("test-nika", "artist-password");
        mvc.perform(get("/api/staff/bookings").header("Authorization", old)).andExpect(status().isOk());

        send(admin, put("/api/admin/users/" + id), """
                {"role":"ARTIST","active":true,"artistId":"%s"}""".formatted(artist("Kai").getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistName").value("Kai"));
        // The old token still names Nika: it stops working instead of showing Nika's bookings
        mvc.perform(get("/api/staff/bookings").header("Authorization", old)).andExpect(status().isUnauthorized());
        login("test-nika", "artist-password").andExpect(jsonPath("$.user.artistId").value(artist("Kai").getId()));
    }

    @Test
    void blockingResettingAndDeleting() throws Exception {
        String admin = adminBearer();
        String id = create(admin, CUSTOMER);
        String customer = bearer("test-mila@example.com", "customer-password");
        Booking booking = booking("Nika", TUESDAY, "10:00", BookingStatus.BOOKED);
        booking.setUserId(id);
        bookings.save(booking);

        send(admin, put("/api/admin/users/" + id), """
                {"role":"USER","active":false,"name":"Mila Test","phone":"0888 222 333"}""")
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/account").header("Authorization", customer)).andExpect(status().isUnauthorized());
        login("test-mila@example.com", "customer-password").andExpect(status().isUnauthorized());

        send(admin, put("/api/admin/users/" + id), """
                {"role":"USER","active":true,"name":"Mila Test","phone":"0888 222 333"}""").andExpect(status().isOk());
        send(admin, put("/api/admin/users/" + id + "/password"), """
                {"password":"brand-new-password"}""").andExpect(status().isNoContent());
        login("test-mila@example.com", "customer-password").andExpect(status().isUnauthorized());
        login("test-mila@example.com", "brand-new-password").andExpect(status().isOk());

        mvc.perform(delete("/api/admin/users/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        // The booking stays for the shop's records, without the account
        org.assertj.core.api.Assertions.assertThat(bookings.findById(booking.getId()).orElseThrow().getUserId()).isNull();
    }

    @Test
    void theAdminCannotLockThemselvesOut() throws Exception {
        String admin = adminBearer();
        String selfId = users.findByUsername("admin").orElseThrow().getId();
        send(admin, put("/api/admin/users/" + selfId), """
                {"role":"USER","active":true}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        send(admin, put("/api/admin/users/" + selfId), """
                {"role":"ADMIN","active":false}""").andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/users/" + selfId).header("Authorization", admin)).andExpect(status().isConflict());
        login("admin", ADMIN_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void rulesForRolesAndUsernames() throws Exception {
        String admin = adminBearer();
        create(admin, """
                {"role":"ARTIST","username":"test-nika","artistId":"%s","password":"artist-password"}""".formatted(artist("Nika").getId()));
        // One login per artist
        send(admin, post("/api/admin/users"), """
                {"role":"ARTIST","username":"test-nika2","artistId":"%s","password":"artist-password"}""".formatted(artist("Nika").getId()))
                .andExpect(status().isConflict());
        // An artist login needs its artist; staff usernames have a fixed format; passwords 8+
        send(admin, post("/api/admin/users"), """
                {"role":"ARTIST","username":"test-x","password":"artist-password"}""").andExpect(status().isBadRequest());
        send(admin, post("/api/admin/users"), """
                {"role":"ADMIN","username":"no spaces!","password":"boss-password"}""").andExpect(status().isBadRequest());
        send(admin, post("/api/admin/users"), """
                {"role":"ADMIN","username":"test-short","password":"short"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        // The username is taken
        send(admin, post("/api/admin/users"), """
                {"role":"ADMIN","username":"Test-Nika","password":"boss-password"}""").andExpect(status().isConflict());
        // Staff logins have no customer details, so they can't become customers
        String staffId = users.findByUsername("test-nika").orElseThrow().getId();
        send(admin, put("/api/admin/users/" + staffId), """
                {"role":"USER","active":true,"name":"Nia","phone":"0888 000 000"}""").andExpect(status().isConflict());
    }

    @Test
    void countsSignIns() throws Exception {
        String admin = adminBearer();
        create(admin, CUSTOMER);
        login("test-mila@example.com", "customer-password").andExpect(status().isOk());
        login("test-mila@example.com", "customer-password").andExpect(status().isOk());
        login("test-mila@example.com", "wrong-password").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", admin).param("q", "test-mila"))
                .andExpect(jsonPath("$.items[0].loginCount").value(2))
                .andExpect(jsonPath("$.items[0].lastLoginAt").isString());
    }

    @Test
    void onlyTheAdminManagesLogins() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", artistBearer("Dario"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }
}
