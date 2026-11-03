package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.tattoostudio.model.Artist;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.WorkingHours;
import com.tattoostudio.service.AnonymizationJob;
import com.tattoostudio.service.AvailabilityService;
import com.tattoostudio.service.BookingService;
import com.tattoostudio.support.ApiTest;

/** An artist's own bookings and hours (/api/staff/**). */
class StaffApiTest extends ApiTest {

    @Autowired
    AvailabilityService availability;
    @Autowired
    BookingService bookingService;
    @Autowired
    AnonymizationJob anonymizationJob;

    private String nia;
    private List<WorkingHours> niaHours;

    @BeforeEach
    void signInAsNia() throws Exception {
        nia = artistBearer("Nia");
        niaHours = artist("Nia").getWeeklyHours();
    }

    @AfterEach
    void restoreNiaHours() {
        Artist b = artist("Nia");
        b.setWeeklyHours(niaHours);
        artists.save(b);
    }

    private ResultActions setStatus(String token, Booking booking, String status) throws Exception {
        return mvc.perform(patch("/api/staff/bookings/" + booking.getId()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    @Test
    void artistSeesOnlyTheirOwnBookingsWithFullDetails() throws Exception {
        booking("Nia", TUESDAY, "11:00", BookingStatus.BOOKED);
        booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        booking("Sol", TUESDAY, "10:00", BookingStatus.BOOKED);

        mvc.perform(get("/api/staff/bookings").header("Authorization", nia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].time").value("10:00")) // earliest first
                .andExpect(jsonPath("$.items[0].artistName").value("Nia Banks"))
                .andExpect(jsonPath("$.items[0].customerName").value("Ivan Petrov"))
                .andExpect(jsonPath("$.items[0].phone").value("+359888123456"))
                .andExpect(jsonPath("$.items[0].email").value("ivan@example.com"))
                .andExpect(jsonPath("$.items[0].price").value(28.0))
                .andExpect(jsonPath("$.items[0].awaitingStatus").value(false));
    }

    @Test
    void filtersByDateRangeAndStatus() throws Exception {
        booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        booking("Nia", TUESDAY, "12:00", BookingStatus.CANCELLED);
        booking("Nia", TUESDAY.plusDays(1), "10:00", BookingStatus.BOOKED);

        mvc.perform(get("/api/staff/bookings").header("Authorization", nia)
                .param("from", TUESDAY.toString()).param("to", TUESDAY.toString()))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/staff/bookings").header("Authorization", nia).param("status", "BOOKED"))
                .andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/staff/bookings").header("Authorization", nia)
                .param("from", TUESDAY.toString()).param("to", MONDAY.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pastBookingsAwaitAStatusUntilMarkedDoneOrNoShow() throws Exception {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);

        // Not possible before the appointment starts
        setStatus(nia, booking, "DONE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        clock.set(at(TUESDAY, "11:30"));
        nia = bearer("test-nia", ARTIST_PASSWORD); // the Monday token has expired by now
        mvc.perform(get("/api/staff/bookings").header("Authorization", nia))
                .andExpect(jsonPath("$.items[0].awaitingStatus").value(true));

        setStatus(nia, booking, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.awaitingStatus").value(false));
        // A mistake can be corrected
        setStatus(nia, booking, "NO_SHOW").andExpect(jsonPath("$.status").value("NO_SHOW"));
        // ...but a finished booking cannot be cancelled
        setStatus(nia, booking, "CANCELLED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));
    }

    @Test
    void artistCanCancelAtAnyTimeAndTheSlotOpensAgain() throws Exception {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        clock.set(at(TUESDAY, "09:30")); // too late for the customer to cancel online, fine for the shop
        nia = bearer("test-nia", ARTIST_PASSWORD);

        setStatus(nia, booking, "CANCELLED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(bookings.findById(booking.getId()).orElseThrow().getCancelledAt()).isNotNull();
        clock.set(NOW);
        assertThat(availability.isFree(artist("Nia"), at(TUESDAY, "10:00"))).isTrue();

        // Cancelled stays cancelled
        setStatus(nia, booking, "DONE").andExpect(status().isConflict());
    }

    @Test
    void statusChangesNeverBringBackWipedCustomerDetails() throws Exception {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        clock.set(at(TUESDAY.plusDays(2), "12:00"));
        nia = bearer("test-nia", ARTIST_PASSWORD);
        anonymizationJob.run(); // more than 24 h after the appointment

        setStatus(nia, booking, "DONE").andExpect(status().isOk());
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DONE);
        assertThat(saved.isAnonymized()).isTrue();
        assertThat(saved.getCustomerName()).isNull();
        assertThat(saved.getPhone()).isNull();
    }

    @Test
    void statusOnlyChangesIfNobodyChangedItMeanwhile() {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        // Someone else cancelled it first: a change that expects BOOKED does nothing
        assertThat(bookingService.changeStatus(booking.getId(), BookingStatus.BOOKED, BookingStatus.CANCELLED)).isPresent();
        assertThat(bookingService.changeStatus(booking.getId(), BookingStatus.BOOKED, BookingStatus.CANCELLED)).isEmpty();
        assertThat(bookings.findById(booking.getId()).orElseThrow().getCancelledAt()).isNotNull();
    }

    @Test
    void otherArtistsBookingsAreInvisible() throws Exception {
        Booking sols = booking("Sol", TUESDAY, "10:00", BookingStatus.BOOKED);
        setStatus(nia, sols, "CANCELLED")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        assertThat(bookings.findById(sols.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void statusCannotBeSetBackToBooked() throws Exception {
        Booking booking = booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        setStatus(nia, booking, "BOOKED").andExpect(status().isBadRequest());
        setStatus(nia, booking, "PARTY").andExpect(status().isBadRequest());
    }

    @Test
    void adminHasNoOwnBookingsHere() throws Exception {
        mvc.perform(get("/api/staff/bookings").header("Authorization", adminBearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void artistEditsTheirHoursAndDaysOff() throws Exception {
        mvc.perform(get("/api/staff/schedule").header("Authorization", nia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY"))
                .andExpect(jsonPath("$.exceptions.length()").value(0));

        mvc.perform(put("/api/staff/schedule").header("Authorization", nia).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"weeklyHours":[{"day":"WEDNESDAY","start":"12:00","end":"18:00"},
                                        {"day":"TUESDAY","start":"10:00","end":"14:00"}],
                         "exceptions":[{"date":"%s","closed":false,"start":"15:00","end":"17:00","note":" Late start "},
                                       {"date":"%s","closed":true,"start":"10:00","end":"12:00","note":"Holiday"},
                                       {"date":"2020-01-01","closed":true,"start":null,"end":null,"note":"Long ago"}]}
                        """.formatted(TUESDAY.plusDays(1), TUESDAY)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours[0].day").value("TUESDAY")) // sorted
                .andExpect(jsonPath("$.weeklyHours[0].end").value("14:00"))
                .andExpect(jsonPath("$.exceptions.length()").value(2)) // the past one is dropped
                .andExpect(jsonPath("$.exceptions[0].date").value(TUESDAY.toString()))
                .andExpect(jsonPath("$.exceptions[0].start").doesNotExist()) // a day off has no hours
                .andExpect(jsonPath("$.exceptions[1].note").value("Late start"));

        // Booking follows the new hours: Tuesday is a day off now, Wednesday only 15:00-17:00
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY)).isEmpty();
        assertThat(availability.freeStarts(artist("Nia"), TUESDAY.plusDays(1)))
                .containsExactly(at(TUESDAY.plusDays(1), "15:00"), at(TUESDAY.plusDays(1), "16:00"));
    }

    @Test
    void newHoursThatClashWithBookingsAreSavedWithAWarning() throws Exception {
        booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        Booking late = booking("Nia", TUESDAY, "15:00", BookingStatus.BOOKED);
        booking("Nia", TUESDAY, "17:00", BookingStatus.CANCELLED); // cancelled ones don't count

        // Tuesday now ends at 14:00: the 15:00 booking no longer fits, but the hours are saved
        mvc.perform(put("/api/staff/schedule").header("Authorization", nia).contentType(MediaType.APPLICATION_JSON)
                .content("{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"10:00\",\"end\":\"14:00\"}],\"exceptions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyHours[0].end").value("14:00"))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].time").value("15:00"))
                .andExpect(jsonPath("$.conflicts[0].customerName").value("Ivan Petrov"));

        // A day off on Tuesday: both booked appointments clash, and the warning stays on reload
        mvc.perform(put("/api/staff/schedule").header("Authorization", nia).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"weeklyHours":[{"day":"TUESDAY","start":"10:00","end":"20:00"}],
                         "exceptions":[{"date":"%s","closed":true}]}
                        """.formatted(TUESDAY)))
                .andExpect(jsonPath("$.conflicts.length()").value(2));
        mvc.perform(get("/api/staff/schedule").header("Authorization", nia))
                .andExpect(jsonPath("$.conflicts.length()").value(2));

        // Once handled (cancelled), the warning goes away
        setStatus(nia, late, "CANCELLED").andExpect(status().isOk());
        mvc.perform(get("/api/staff/schedule").header("Authorization", nia))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].time").value("10:00"));
    }

    @Test
    void invalidHoursAreRejected() throws Exception {
        String[] bad = {
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"18:00\",\"end\":\"10:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"10:00\",\"end\":\"12:00\"},{\"day\":\"TUESDAY\",\"start\":\"13:00\",\"end\":\"15:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[],\"exceptions\":[{\"date\":\"%s\",\"closed\":false,\"start\":null,\"end\":null}]}".formatted(TUESDAY),
                // Start times must stay on the full hour, so bookings always line up with the hours
                "{\"weeklyHours\":[{\"day\":\"TUESDAY\",\"start\":\"10:30\",\"end\":\"18:00\"}],\"exceptions\":[]}",
                "{\"weeklyHours\":[],\"exceptions\":[{\"date\":\"%s\",\"closed\":false,\"start\":\"09:15\",\"end\":\"12:00\"}]}".formatted(TUESDAY),
                "{\"weeklyHours\":[]}",
        };
        for (String body : bad) {
            mvc.perform(put("/api/staff/schedule").header("Authorization", nia)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(artist("Nia").getWeeklyHours()).isEqualTo(niaHours);
    }
}
