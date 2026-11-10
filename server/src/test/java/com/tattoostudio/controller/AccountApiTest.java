package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;


import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.support.ApiTest;

/** Customer accounts: sign up, sign in with the email, book while signed in and "My account". */
class AccountApiTest extends ApiTest {

    private ResultActions register(String name, String phone, String email, String password) throws Exception {
        return register(name, phone, email, password, "1995-05-05");
    }

    private ResultActions register(String name, String phone, String email, String password, String dateOfBirth)
            throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(visitor);
                    return request;
                })
                .content("""
                        {"name":"%s","phone":"%s","email":"%s","dateOfBirth":"%s","password":"%s"}
                        """.formatted(name, phone, email, dateOfBirth, password)));
    }

    /** Signs up and returns the "Authorization" header value. */
    private String customer(String email) throws Exception {
        String body = register("Mira Petrova", "0888 123 456", email, "customer-password")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    /** Books a flash design on Tuesday, signed in with {@code auth} (or as a guest). */
    private ResultActions book(String auth, String design, String time, String phone) throws Exception {
        String json = flashJson(flash(design).getId(), TUESDAY, time).replace("0888 123 456", phone);
        return postJson("/api/bookings/flash", json, auth);
    }

    private String code(ResultActions created) throws Exception {
        return JsonPath.read(created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.code");
    }

    @Test
    void signUpSignsInAndBookingsShowUpInMyAccount() throws Exception {
        register("Mira Petrova", "0888 123 456", "Test-Mira@Example.com", "customer-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("test-mira@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.name").value("Mira Petrova"));

        // Sign in later with the email (not case-sensitive)
        String body = login("TEST-mira@example.com", "customer-password").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(body, "$.token");

        mvc.perform(get("/api/account").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mira Petrova"))
                .andExpect(jsonPath("$.email").value("test-mira@example.com"))
                .andExpect(jsonPath("$.phone").value("0888 123 456"))
                .andExpect(jsonPath("$.dateOfBirth").value("1995-05-05"));

        String early = code(book(auth, "Small dragon", "11:00", "0888 123 456"));
        String late = code(book(auth, "Peony", "15:00", "0888 123 456"));
        code(book(null, "Oni", "12:00", "0899 111 222")); // a guest booking: not in anyone's account

        mvc.perform(get("/api/account/bookings").param("when", "ALL").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].code").value(late)) // latest appointment first
                .andExpect(jsonPath("$.items[1].code").value(early))
                .andExpect(jsonPath("$.items[0].artistName").value("Nika"))
                .andExpect(jsonPath("$.items[0].canPayDeposit").value(true))
                .andExpect(jsonPath("$.items[0].cancellable").value(true));
        mvc.perform(get("/api/account/bookings").header("Authorization", auth)) // upcoming, soonest first
                .andExpect(jsonPath("$.items[0].code").value(early));
    }

    @Test
    void customersPayDepositsAndSeeConsultationsAndSessions() throws Exception {
        String auth = customer("test-deposit@example.com");
        String code = code(book(auth, "Small dragon", "11:00", "0888 123 456"));
        mvc.perform(post("/api/account/bookings/{code}/deposit", code).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").isString())
                .andExpect(jsonPath("$.amount").value(30));

        // A consultation made from the account, and the session the artist reserves after it
        String userId = users.findByUsername("test-deposit@example.com").orElseThrow().getId();
        Booking consultation = booking("Dario", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        consultation.setUserId(userId);
        bookings.save(consultation);
        mvc.perform(post("/api/staff/sessions").header("Authorization", artistBearer("Dario"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"customerBookingId":"%s","date":"%s","start":"12:00","end":"16:00",
                                 "description":"Back piece, session 1 of 3","price":480,"deposit":100}
                                """.formatted(consultation.getId(), TUESDAY.plusDays(2))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/account/bookings").header("Authorization", auth))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].type").value("SESSION"))
                .andExpect(jsonPath("$.items[1].title").value("Back piece, session 1 of 3"))
                .andExpect(jsonPath("$.items[1].deposit.amount").value(100))
                .andExpect(jsonPath("$.items[1].leftToPay").value(380));
        mvc.perform(get("/api/account/bookings").param("when", "ALL").param("type", "CONSULTATION")
                        .header("Authorization", auth))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].consultation.idea").value("A koi swimming up through waves"));
    }

    @Test
    void customersReviewTheirFinishedAppointments() throws Exception {
        String auth = customer("test-review@example.com");
        Booking done = booking("Kai", MONDAY.minusDays(1), "14:00", BookingStatus.DONE);
        done.setUserId(users.findByUsername("test-review@example.com").orElseThrow().getId());
        bookings.save(done);

        mvc.perform(get("/api/account/bookings").param("when", "PAST").header("Authorization", auth))
                .andExpect(jsonPath("$.items[0].canReview").value(true));
        mvc.perform(post("/api/account/bookings/{code}/review", done.getCode()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"text\":\"Bright and solid\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/account/bookings").param("when", "PAST").header("Authorization", auth))
                .andExpect(jsonPath("$.items[0].canReview").value(false));
    }

    @Test
    void anAccountHoldsAtMostTwoUpcomingBookingsWhateverThePhone() throws Exception {
        String mira = customer("test-hoard@example.com");
        code(book(mira, "Small dragon", "11:00", "0888 000 001"));
        code(book(mira, "Peony", "11:00", "0888 000 002"));
        book(mira, "Oni", "11:00", "0888 000 003")
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
        code(book(old, "Small dragon", "11:00", "0888 123 456"));
        String own = code(book(fresh, "Peony", "11:00", "0888 123 456"));
        mvc.perform(get("/api/account/bookings").header("Authorization", fresh))
                .andExpect(jsonPath("$.totalItems").value(1));

        mvc.perform(post("/api/account/delete").header("Authorization", fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"customer-password\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/account/delete").header("Authorization", fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"brand-new-password\"}")).andExpect(status().isNoContent());
        login("test-self@example.com", "brand-new-password").andExpect(status().isUnauthorized());
        assertThat(bookings.findByCode(own).orElseThrow().getUserId()).isNull();
    }

    @Test
    void customersChangeTheirDetailsAndTheNewEmailSignsIn() throws Exception {
        customer("test-taken@example.com");
        String old = customer("test-details@example.com");
        mvc.perform(put("/api/account").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mira P.\",\"phone\":\"0899 111 222\",\"email\":\"Test-Taken@example.com\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/account").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"12\",\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists());

        String body = mvc.perform(put("/api/account").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Mira P. \",\"phone\":\"0899 111 222\",\"email\":\"Test-New@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("test-new@example.com"))
                .andExpect(jsonPath("$.user.name").value("Mira P."))
                .andReturn().getResponse().getContentAsString();
        String fresh = "Bearer " + JsonPath.read(body, "$.token");
        mvc.perform(get("/api/account").header("Authorization", fresh))
                .andExpect(jsonPath("$.email").value("test-new@example.com"))
                .andExpect(jsonPath("$.phone").value("0899 111 222"));
        // The old sign-in named the old email; the old email no longer signs in, the new one does
        mvc.perform(get("/api/account").header("Authorization", old)).andExpect(status().isUnauthorized());
        login("test-details@example.com", "customer-password").andExpect(status().isUnauthorized());
        login("test-new@example.com", "customer-password").andExpect(status().isOk());
    }

    @Test
    void aNewAccountWithADeletedAccountsEmailStartsWithoutItsOldSignIns() throws Exception {
        String old = customer("test-again@example.com");
        mvc.perform(post("/api/account/delete").header("Authorization", old).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"customer-password\"}")).andExpect(status().isNoContent());
        String fresh = customer("test-again@example.com");

        // The old sign-in (say, still open on another phone) belongs to the deleted account, not the new one
        mvc.perform(get("/api/account").header("Authorization", old)).andExpect(status().isUnauthorized());
        String guest = code(book(old, "Small dragon", "11:00", "0888 123 456"));
        assertThat(bookings.findByCode(guest).orElseThrow().getUserId()).isNull();
        mvc.perform(get("/api/account/bookings").header("Authorization", fresh)).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void oneAccountPerEmail() throws Exception {
        customer("test-mira2@example.com");
        register("Someone Else", "0899 000 111", "TEST-Mira2@example.com", "another-password")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void customerCancelsOnlyTheirOwnBookingsAndOnlyBeforeTheyStart() throws Exception {
        String mira = customer("test-mira3@example.com");
        String theo = customer("test-theo@example.com");
        String code = code(book(mira, "Small dragon", "11:00", "0888 123 456"));

        mvc.perform(post("/api/account/bookings/{code}/cancel", code).header("Authorization", theo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));

        String second = code(book(mira, "Peony", "16:00", "0888 123 456"));
        mvc.perform(post("/api/account/bookings/{code}/cancel", code).header("Authorization", mira))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.deposit.status").value("NOT_PAID")); // it was never paid

        // Once the appointment has started it can't be cancelled online (sign in again: the clock jump expires the token)
        Booking held = bookings.findByCode(second).orElseThrow();
        held.changeStatus(BookingStatus.BOOKED); // as if the deposit had been paid
        held.setDepositDueAt(null);
        bookings.save(held);
        clock.set(at(TUESDAY, "16:00"));
        String again = "Bearer " + JsonPath.read(login("test-mira3@example.com", "customer-password")
                .andReturn().getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/account/bookings/{code}/cancel", second).header("Authorization", again))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
    }

    @Test
    void customersStayOutOfStaffPagesAndStaffOutOfAccounts() throws Exception {
        String mira = customer("test-mira4@example.com");
        mvc.perform(get("/api/staff/bookings").header("Authorization", mira)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stats").header("Authorization", mira)).andExpect(status().isForbidden());
        mvc.perform(get("/api/account").header("Authorization", adminBearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // A booking by staff while signed in is not linked to the staff login
        code(book(adminBearer(), "Oni", "13:00", "0899 333 444"));
        mvc.perform(get("/api/account/bookings").header("Authorization", mira))
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
        register("Mira", "12", "test-x@example.com", "customer-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE"));
        register("Mira", "0888 123 456", "test-young@example.com", "customer-password",
                MONDAY.minusYears(16).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDER_AGE"));
    }

    /** BCrypt reads at most 72 bytes: longer passwords are turned down with a 400, never a server error. */
    @Test
    void overLongPasswordsAreTurnedDownNotAServerError() throws Exception {
        register("Mira", "0888 123 456", "test-long@example.com", "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("must be 8 to 72 characters"));
        // 40 Cyrillic letters fit the 72-character limit but take 80 bytes
        register("Mira", "0888 123 456", "test-long@example.com", "Ж".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        register("Mira", "0888 123 456", "test-long@example.com", "a".repeat(72)).andExpect(status().isCreated());
        login("test-long@example.com", "a".repeat(150)).andExpect(status().isUnauthorized());
    }

    @Test
    void failedSignUpsDoNotUseUpTheLimit() throws Exception {
        customer("test-taken@example.com");
        for (int i = 0; i < 6; i++) {
            register("Mira", "12", "test-typo@example.com", "customer-password").andExpect(status().isBadRequest());
            register("Mira", "0888 123 456", "test-taken@example.com", "customer-password").andExpect(status().isConflict());
        }
        customer("test-finally@example.com");
    }

    @Test
    void oneVisitorCanCreateFiveAccountsPerHour() throws Exception {
        for (int i = 1; i <= 5; i++) {
            customer("test-many" + i + "@example.com");
        }
        register("Mira", "0888 123 456", "test-many6@example.com", "customer-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SUBMISSIONS"));
        clock.advance(Duration.ofMinutes(61));
        customer("test-many6@example.com");
    }
}
