package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.service.AnonymizationJob;
import com.tattoostudio.service.AvailabilityService;
import com.tattoostudio.service.BookingService;
import com.tattoostudio.support.ApiTest;

class StaffApiTest extends ApiTest {

    @Autowired
    AvailabilityService availability;
    @Autowired
    BookingService bookingService;
    @Autowired
    AnonymizationJob anonymizationJob;

    private String nika;
    private List<WorkingHours> nikaHours;

    @BeforeEach
    void signInAsNika() throws Exception {
        nika = artistBearer("Nika");
        nikaHours = artist("Nika").getWeeklyHours();
    }

    @AfterEach
    void restoreNikaHours() {
        Artist a = artist("Nika");
        a.setWeeklyHours(nikaHours);
        artists.save(a);
    }

    private ResultActions setStatus(String token, Booking booking, String status) throws Exception {
        return mvc.perform(patch("/api/staff/bookings/" + booking.getId()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    // --- Calendar ---

    @Test
    void artistSeesOnlyTheirOwnBookingsWithFullDetails() throws Exception {
        booking("Nika", TUESDAY, "14:00", BookingStatus.BOOKED);
        booking("Nika", TUESDAY, "11:00", 30, BookingType.CONSULTATION, BookingStatus.BOOKED);
        booking("Dario", TUESDAY, "11:00", BookingStatus.BOOKED);

        mvc.perform(get("/api/staff/bookings").header("Authorization", nika))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].time").value("11:00")) // earliest first
                .andExpect(jsonPath("$.items[0].endTime").value("11:30"))
                .andExpect(jsonPath("$.items[0].type").value("CONSULTATION"))
                .andExpect(jsonPath("$.items[0].artistName").value("Nika"))
                .andExpect(jsonPath("$.items[0].customerName").value("Ivan Petrov"))
                .andExpect(jsonPath("$.items[0].phone").value("+359888123456"))
                .andExpect(jsonPath("$.items[0].consultation.idea").value("A koi swimming up through waves"))
                .andExpect(jsonPath("$.items[1].price").value(100))
                .andExpect(jsonPath("$.items[1].awaitingStatus").value(false));
        mvc.perform(get("/api/staff/bookings").header("Authorization", nika).param("type", "CONSULTATION"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void filtersByDateRangeAndStatus() throws Exception {
        booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        booking("Nika", TUESDAY, "14:00", BookingStatus.CANCELLED);
        booking("Nika", TUESDAY.plusDays(1), "11:00", BookingStatus.BOOKED);

        mvc.perform(get("/api/staff/bookings").header("Authorization", nika)
                        .param("from", TUESDAY.toString()).param("to", TUESDAY.toString()))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/staff/bookings").header("Authorization", nika).param("status", "BOOKED"))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/staff/bookings").header("Authorization", nika)
                        .param("from", TUESDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pastBookingsAwaitAStatusUntilMarkedDoneOrNoShow() throws Exception {
        Booking booking = booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        setStatus(nika, booking, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        clock.set(at(TUESDAY, "12:30"));
        nika = bearer("test-nika", ARTIST_PASSWORD); // the Monday token has expired by now
        mvc.perform(get("/api/staff/bookings").header("Authorization", nika))
                .andExpect(jsonPath("$.items[0].awaitingStatus").value(true));

        setStatus(nika, booking, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.awaitingStatus").value(false));
        setStatus(nika, booking, "NO_SHOW").andExpect(jsonPath("$.status").value("NO_SHOW")); // a mistake can be corrected
        setStatus(nika, booking, "CANCELLED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));
    }

    @Test
    void artistCanCancelAtAnyTimeRefundingTheDeposit() throws Exception {
        String code = JsonPath.read(bookFlash("Peony", TUESDAY, "14:00"), "$.code");
        bookingService.depositPaid(bookings.findByCode(code).orElseThrow().getPaymentId());
        Booking booking = bookings.findByCode(code).orElseThrow();
        clock.set(at(TUESDAY, "13:30")); // too late for the customer to get the deposit back, but the studio cancels
        nika = bearer("test-nika", ARTIST_PASSWORD);

        setStatus(nika, booking, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.deposit.status").value("REFUNDED"));
        clock.set(NOW);
        assertThat(availability.isFree(artist("Nika"), at(TUESDAY, "14:00"), Duration.ofHours(1))).isTrue();
        setStatus(nika, booking, "DONE").andExpect(status().isConflict()); // cancelled stays cancelled
    }

    @Test
    void statusChangesNeverBringBackWipedCustomerDetails() throws Exception {
        Booking booking = booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        clock.set(at(TUESDAY, "12:00").plus(Duration.ofDays(181)));
        nika = bearer("test-nika", ARTIST_PASSWORD);
        anonymizationJob.run();

        setStatus(nika, booking, "DONE").andExpect(status().isOk());
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DONE);
        assertThat(saved.isAnonymized()).isTrue();
        assertThat(saved.getCustomerName()).isNull();
        assertThat(saved.getPhone()).isNull();
    }

    @Test
    void statusOnlyChangesIfNobodyChangedItMeanwhile() {
        Booking booking = booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        assertThat(bookingService.changeStatus(booking, BookingStatus.NO_SHOW)).isPresent();
        assertThat(bookingService.changeStatus(booking, BookingStatus.DONE)).isEmpty(); // it isn't BOOKED any more
    }

    @Test
    void otherArtistsBookingsAreInvisible() throws Exception {
        Booking darios = booking("Dario", TUESDAY, "11:00", BookingStatus.BOOKED);
        setStatus(nika, darios, "CANCELLED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        assertThat(bookings.findById(darios.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void statusCannotBeSetBackToBooked() throws Exception {
        Booking booking = booking("Nika", TUESDAY, "11:00", BookingStatus.BOOKED);
        setStatus(nika, booking, "BOOKED").andExpect(status().isBadRequest());
        setStatus(nika, booking, "PARTY").andExpect(status().isBadRequest());
    }

    @Test
    void adminHasNoOwnBookingsHere() throws Exception {
        mvc.perform(get("/api/staff/bookings").header("Authorization", adminBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // --- Sessions ---

    @Test
    void artistsReserveSessionsForTheirConsultationCustomers() throws Exception {
        String dario = artistBearer("Dario");
        Booking consultation = booking("Dario", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        booking("Nika", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);

        mvc.perform(get("/api/staff/customers").header("Authorization", dario).param("q", "0888 123 456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(consultation.getId()))
                .andExpect(jsonPath("$[0].name").value("Ivan Petrov"))
                .andExpect(jsonPath("$[0].about").value("A koi swimming up through waves"));

        String session = """
                {"customerBookingId":"%s","date":"%s","start":"11:00","end":"15:00",
                 "description":"Back piece, session 1 of 3","price":480,"deposit":100}
                """.formatted(consultation.getId(), TUESDAY.plusDays(4));
        String body = mvc.perform(post("/api/staff/sessions").header("Authorization", dario)
                        .contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SESSION"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.title").value("Back piece, session 1 of 3"))
                .andExpect(jsonPath("$.endTime").value("15:00"))
                .andExpect(jsonPath("$.deposit.amount").value(100))
                .andExpect(jsonPath("$.deposit.status").value("WAITING"))
                .andExpect(jsonPath("$.customerName").value("Ivan Petrov"))
                .andExpect(jsonPath("$.followsBookingId").value(consultation.getId()))
                .andReturn().getResponse().getContentAsString();
        Booking saved = bookings.findById(JsonPath.read(body, "$.id")).orElseThrow();
        assertThat(saved.getDepositDueAt()).isEqualTo(NOW.plus(Duration.ofHours(72)));

        // The time is gone from online booking, the break after it included
        assertThat(availability.isFree(artist("Dario"), at(TUESDAY.plusDays(4), "15:00"), Duration.ofMinutes(30))).isFalse();
        assertThat(availability.isFree(artist("Dario"), at(TUESDAY.plusDays(4), "15:30"), Duration.ofMinutes(30))).isTrue();
        // The same time can't be reserved twice
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario)
                        .contentType(MediaType.APPLICATION_JSON).content(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        // The customer pays the deposit with the code and phone number
        postJson("/api/bookings/deposit", "{\"code\":\"%s\",\"phone\":\"0888123456\"}".formatted(saved.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100));
    }

    @Test
    void aSessionCanTakeTheTimeOfALapsedFlashHold() throws Exception {
        String kai = artistBearer("Kai");
        Booking own = booking("Kai", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        bookFlash("Small dragon", TUESDAY, "14:00"); // held for 15 minutes
        clock.advance(Duration.ofMinutes(16)); // ran out, and the hold job hasn't run yet
        kai = bearer("test-kai", ARTIST_PASSWORD);
        mvc.perform(post("/api/staff/sessions").header("Authorization", kai).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerBookingId":"%s","date":"%s","start":"14:00","end":"16:00",
                                 "description":"Sleeve, session 1","price":300,"deposit":0}
                                """.formatted(own.getId(), TUESDAY)))
                .andExpect(status().isCreated());
    }

    @Test
    void sessionsAreOnlyForTheArtistsOwnCustomersAndOnTheGrid() throws Exception {
        String dario = artistBearer("Dario");
        Booking nikasCustomer = booking("Nika", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        Booking own = booking("Dario", MONDAY.minusDays(1), "12:00", 30, BookingType.CONSULTATION, BookingStatus.DONE);
        String template = """
                {"customerBookingId":"%s","date":"%s","start":"%s","end":"%s","description":"Sleeve","price":%d,"deposit":%d}
                """;
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(nikasCustomer.getId(), TUESDAY, "11:00", "15:00", 400, 100)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(own.getId(), TUESDAY, "11:15", "15:00", 400, 100)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(own.getId(), TUESDAY, "15:00", "11:00", 400, 100)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(own.getId(), TUESDAY, "11:00", "15:00", 100, 400)))
                .andExpect(status().isBadRequest());
        // Without a deposit the session is simply booked
        mvc.perform(post("/api/staff/sessions").header("Authorization", dario).contentType(MediaType.APPLICATION_JSON)
                        .content(template.formatted(own.getId(), TUESDAY, "11:00", "15:00", 400, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deposit.status").value(DepositStatus.NONE.name()));
    }

    // --- Hours ---

    @Test
    void artistEditsTheirHoursDaysOffAndBreak() throws Exception {
        mvc.perform(get("/api/staff/schedule").header("Authorization", nika))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY"))
                .andExpect(jsonPath("$.breakMinutes").value(30))
                .andExpect(jsonPath("$.exceptions.length()").value(0));

        mvc.perform(put("/api/staff/schedule").header("Authorization", nika).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyHours":[{"day":"WEDNESDAY","start":"12:00","end":"18:00"},
                                                {"day":"TUESDAY","start":"11:00","end":"14:00"}],
                                 "exceptions":[{"date":"%s","closed":false,"start":"15:30","end":"17:00","note":" Late start "},
                                               {"date":"%s","closed":true,"note":"Tattoo convention, Milan"},
                                               {"date":"2020-01-01","closed":true,"note":"Long ago"}],
                                 "breakMinutes":60}
                                """.formatted(TUESDAY.plusDays(1), TUESDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY")) // sorted
                .andExpect(jsonPath("$.exceptions.length()").value(2)) // the past one is dropped
                .andExpect(jsonPath("$.exceptions[0].start").doesNotExist()) // a day off has no hours
                .andExpect(jsonPath("$.exceptions[1].note").value("Late start"))
                .andExpect(jsonPath("$.breakMinutes").value(60));

        // Booking follows the new hours: Tuesday is a day off now, Wednesday only 15:30-17:00
        var free = availability.freeStartsForDays(artist("Nika"), Duration.ofHours(1), TUESDAY, 2);
        assertThat(free.get(TUESDAY)).isEmpty();
        assertThat(free.get(TUESDAY.plusDays(1)))
                .containsExactly(at(TUESDAY.plusDays(1), "15:30"), at(TUESDAY.plusDays(1), "16:00"));
    }

    @Test
    void newHoursThatClashWithBookingsAreSavedWithAWarning() throws Exception {
        booking("Nika", TUESDAY, "11:00", 60, BookingType.FLASH, BookingStatus.BOOKED);
        Booking late = booking("Nika", TUESDAY, "15:00", 60, BookingType.FLASH, BookingStatus.BOOKED);
        booking("Nika", TUESDAY, "17:00", 60, BookingType.FLASH, BookingStatus.CANCELLED); // cancelled ones don't count
        booking("Nika", TUESDAY, "19:00", BookingStatus.BOOKED); // a reserved session may be outside the hours

        mvc.perform(put("/api/staff/schedule").header("Authorization", nika).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"11:00\",\"end\":\"14:00\"}],\"exceptions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].time").value("15:00"))
                .andExpect(jsonPath("$.conflicts[0].customerName").value("Ivan Petrov"));

        mvc.perform(put("/api/staff/schedule").header("Authorization", nika).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weeklyHours":[{"day":"TUESDAY","start":"11:00","end":"23:00"}],
                                 "exceptions":[{"date":"%s","closed":true}]}
                                """.formatted(TUESDAY)))
                .andExpect(jsonPath("$.conflicts.length()").value(2));

        setStatus(nika, late, "CANCELLED").andExpect(status().isOk());
        mvc.perform(get("/api/staff/schedule").header("Authorization", nika))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].time").value("11:00"));
    }

    @Test
    void invalidHoursAreRejected() throws Exception {
        String[] bad = {
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"18:00\",\"end\":\"10:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"10:00\",\"end\":\"12:00\"},{\"day\":\"TUESDAY\",\"start\":\"13:00\",\"end\":\"15:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[],\"exceptions\":[{\"date\":\"%s\",\"closed\":false,\"start\":null,\"end\":null}]}".formatted(TUESDAY),
                // Start times stay on the half-hour grid, so bookings always line up with the hours
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"10:15\",\"end\":\"18:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[],\"exceptions\":[{\"date\":\"%s\",\"closed\":false,\"start\":\"09:45\",\"end\":\"12:00\"}]}".formatted(TUESDAY),
                "{\"weeklyHours\":[],\"exceptions\":[],\"breakMinutes\":45}",
                "{\"weeklyHours\":[]}",
        };
        for (String body : bad) {
            mvc.perform(put("/api/staff/schedule").header("Authorization", nika)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(artist("Nika").getWeeklyHours()).isEqualTo(nikaHours);
    }

    // --- Flash designs and portfolio ---

    @Test
    void artistsManageTheirOwnFlash() throws Exception {
        MockMultipartFile json = new MockMultipartFile("flash", "", MediaType.APPLICATION_JSON_VALUE, """
                {"name":"%s crane","widthCm":7,"heightCm":11,"durationMinutes":90,"price":120}
                """.formatted(TEST_FLASH).getBytes());
        String body = mvc.perform(multipart("/api/staff/flash").file(json).file(photo("image", "crane.png"))
                        .header("Authorization", nika))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.artistName").value("Nika"))
                .andExpect(jsonPath("$.size").value("MEDIUM"))
                .andExpect(jsonPath("$.state").value("ON_SHEET"))
                .andExpect(jsonPath("$.image.url").exists())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mvc.perform(get("/api/flash/" + id)).andExpect(jsonPath("$.bookable").value(true));

        mvc.perform(patch("/api/staff/flash/" + id).header("Authorization", nika)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(jsonPath("$.state").value("HIDDEN"));
        mvc.perform(get("/api/flash/" + id)).andExpect(jsonPath("$.bookable").value(false));

        mvc.perform(put("/api/staff/flash/" + id).header("Authorization", nika).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s crane\",\"widthCm\":7,\"heightCm\":11,\"durationMinutes\":100,\"price\":120}"
                                .formatted(TEST_FLASH)))
                .andExpect(status().isBadRequest()); // not a multiple of 30 minutes

        // Kai can't touch Nika's designs
        mvc.perform(delete("/api/staff/flash/" + id).header("Authorization", artistBearer("Kai")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/staff/flash/" + id).header("Authorization", nika)).andExpect(status().isNoContent());
        assertThat(flash.findById(id)).isEmpty();
    }

    @Test
    void bookedDesignsCantBeDeletedAndShowTheirState() throws Exception {
        bookFlash("Peony", TUESDAY, "14:00");
        mvc.perform(get("/api/staff/flash").header("Authorization", nika).param("q", "Peony"))
                .andExpect(jsonPath("$.items[0].state").value("HELD"))
                .andExpect(jsonPath("$.items[0].bookingCode").exists());
        mvc.perform(delete("/api/staff/flash/" + flash("Peony").getId()).header("Authorization", nika))
                .andExpect(status().isConflict());
    }

    @Test
    void portfolioPhotosAndTheSignaturePiece() throws Exception {
        String body = mvc.perform(multipart("/api/staff/works").file(photo("image", "sleeve.png"))
                        .file(new MockMultipartFile("caption", "", MediaType.TEXT_PLAIN_VALUE, "Warrior sleeve".getBytes()))
                        .header("Authorization", nika))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caption").value("Warrior sleeve"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/artists/nika/works")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/staff/works/" + JsonPath.read(body, "$.id")).header("Authorization", nika))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/artists/nika/works")).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(multipart("/api/staff/signature").file(photo("image", "peony.png"))
                        .with(r -> {
                            r.setMethod("PUT");
                            return r;
                        })
                        .header("Authorization", nika))
                .andExpect(status().isOk());
        mvc.perform(get("/api/artists/nika")).andExpect(jsonPath("$.signaturePiece.url").exists());

        // The artist takes it off again; another artist's scroll is never touched
        mvc.perform(delete("/api/staff/signature").header("Authorization", nika)).andExpect(status().isNoContent());
        mvc.perform(get("/api/artists/nika")).andExpect(jsonPath("$.signaturePiece").doesNotExist());
        assertThat(artist("Nika").getSignaturePiece()).isNull();
        mvc.perform(delete("/api/staff/signature")).andExpect(status().isUnauthorized());
    }
}
