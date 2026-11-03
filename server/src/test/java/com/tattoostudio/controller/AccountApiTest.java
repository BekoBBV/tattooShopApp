package com.tattoostudio.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.tattoostudio.support.ApiTest;
import com.jayway.jsonpath.JsonPath;

/** Customer accounts: sign up, sign in with the email, book while signed in and "My bookings". */
class AccountApiTest extends ApiTest {

    private ResultActions register(String name, String phone, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(visitor);
                    return request;
                })
                .content("""
                        {"name":"%s","phone":"%s","email":"%s","password":"%s"}
                        """.formatted(name, phone, email, password)));
    }

    /** Signs up and returns the "Authorization" header value. */
    private String customer(String email) throws Exception {
        String body = register("Alex Morgan", "0888 123 456", email, "customer-password")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private ResultActions book(String auth, String time, String phone) throws Exception {
        var request = post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceId":"%s","artistId":null,"date":"%s","time":"%s","customerName":"Alex",
                 "phone":"%s","email":null,"notes":null}
                """.formatted(service("Skin Fade").getId(), TUESDAY, time, phone));
        return mvc.perform(auth == null ? request : request.header("Authorization", auth));
    }

    private String code(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.code");
    }

    @Test
    void signUpSignsInAndBookingsShowUpInMyBookings() throws Exception {
        register("Alex Morgan", "0888 123 456", "Test-Alex@Example.com", "customer-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("test-alex@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.name").value("Alex Morgan"));

        // Sign in later with the email (not case-sensitive)
        String body = login("TEST-alex@example.com", "customer-password").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/account").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex Morgan"))
                .andExpect(jsonPath("$.email").value("test-alex@example.com"))
                .andExpect(jsonPath("$.phone").value("0888 123 456"));

        String early = code(book(auth, "10:00", "0888 123 456"));
        String late = code(book(auth, "15:00", "0888 123 456"));
        code(book(null, "12:00", "0899 111 222")); // a guest booking: not in anyone's account

        mvc.perform(get("/api/account/bookings").param("when", "ALL").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value(late)) // latest appointment first
                .andExpect(jsonPath("$.items[1].code").value(early))
                .andExpect(jsonPath("$.items[0].artistName").isString())
                .andExpect(jsonPath("$.items[0].cancellable").value(true));
    }

    @Test
    void anAccountHoldsAtMostTwoUpcomingBookingsWhateverThePhone() throws Exception {
        String alex = customer("test-hoard@example.com");
        code(book(alex, "10:00", "0888 000 001"));
        code(book(alex, "11:00", "0888 000 002"));
        book(alex, "12:00", "0888 000 003")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_LIMIT_REACHED"));
    }

    @Test
    void customersChangeTheirPasswordAndDeleteTheirAccount() throws Exception {
        String old = customer("test-self@example.com");
        mvc.perform(put("/api/account/password").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"brand-new-password\"}"))
                .andExpect(status().isBadRequest());
        clock.advance(Duration.ofSeconds(2)); // tokens count whole seconds
        String body = mvc.perform(put("/api/account/password").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"customer-password\",\"newPassword\":\"brand-new-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fresh = "Bearer " + JsonPath.read(body, "$.token");
        mvc.perform(get("/api/account").header("Authorization", old)).andExpect(status().isUnauthorized());

        // A stale sign-in doesn't stop a booking: it simply becomes a guest booking
        code(book(old, "10:00", "0888 123 456"));
        String own = code(book(fresh, "11:00", "0888 123 456"));
        mvc.perform(get("/api/account/bookings").header("Authorization", fresh))
                .andExpect(jsonPath("$.totalItems").value(1));

        mvc.perform(post("/api/account/delete").header("Authorization", fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"customer-password\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/delete").header("Authorization", fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"brand-new-password\"}")).andExpect(status().isNoContent());
        login("test-self@example.com", "brand-new-password").andExpect(status().isUnauthorized());
        org.assertj.core.api.Assertions.assertThat(bookings.findByCode(own).orElseThrow().getUserId()).isNull();
    }

    @Test
    void aNewAccountWithADeletedAccountsEmailStartsWithoutItsOldSignIns() throws Exception {
        String old = customer("test-again@example.com");
        mvc.perform(post("/api/account/delete").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"customer-password\"}")).andExpect(status().isNoContent());
        String fresh = customer("test-again@example.com");

        // The old sign-in (say, still open on another phone) belongs to the deleted account, not the new one
        mvc.perform(get("/api/account").header("Authorization", old)).andExpect(status().isUnauthorized());
        String guest = code(book(old, "10:00", "0888 123 456"));
        org.assertj.core.api.Assertions.assertThat(bookings.findByCode(guest).orElseThrow().getUserId()).isNull();
        mvc.perform(get("/api/account/bookings").header("Authorization", fresh)).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void oneAccountPerEmail() throws Exception {
        customer("test-mira@example.com");
        register("Someone Else", "0899 000 111", "TEST-Mira@example.com", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void customerCancelsOnlyTheirOwnBookingsAndOnlyInTime() throws Exception {
        String alex = customer("test-alex2@example.com");
        String theo = customer("test-theo@example.com");
        String code = code(book(alex, "11:00", "0888 123 456"));

        mvc.perform(post("/api/account/bookings/{code}/cancel", code).header("Authorization", theo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        String second = code(book(alex, "16:00", "0888 123 456"));
        mvc.perform(post("/api/account/bookings/{code}/cancel", code).header("Authorization", alex))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Online cancellation closes 2 hours before (sign in again: the clock jump expires the token)
        clock.set(at(TUESDAY, "14:30"));
        String again = "Bearer " + JsonPath.read(login("test-alex2@example.com", "customer-password")
                .andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/account/bookings/{code}/cancel", second).header("Authorization", again))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
    }

    @Test
    void customersStayOutOfStaffPagesAndStaffOutOfAccounts() throws Exception {
        String alex = customer("test-alex3@example.com");
        mvc.perform(get("/api/staff/bookings").header("Authorization", alex)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").header("Authorization", alex)).andExpect(status().isForbidden());
        mvc.perform(get("/api/account").header("Authorization", adminBearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // A booking by staff while signed in is not linked to the staff login
        code(book(adminBearer(), "13:00", "0899 333 444"));
        mvc.perform(get("/api/account/bookings").header("Authorization", alex))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void signUpChecksEveryField() throws Exception {
        register("", "0888 123 456", "not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        register("Alex", "12", "test-x@example.com", "customer-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE"));
    }

    @Test
    void failedSignUpsDoNotUseUpTheLimit() throws Exception {
        customer("test-taken@example.com");
        for (int i = 0; i < 6; i++) {
            register("Alex", "12", "test-typo@example.com", "customer-password").andExpect(status().isBadRequest());
            register("Alex", "0888 123 456", "test-taken@example.com", "customer-password").andExpect(status().isConflict());
        }
        customer("test-finally@example.com");
    }

    @Test
    void oneVisitorCanCreateFiveAccountsPerHour() throws Exception {
        for (int i = 1; i <= 5; i++) {
            customer("test-many" + i + "@example.com");
        }
        register("Alex", "0888 123 456", "test-many6@example.com", "customer-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SUBMISSIONS"));
        clock.advance(Duration.ofMinutes(61));
        customer("test-many6@example.com");
    }
}
