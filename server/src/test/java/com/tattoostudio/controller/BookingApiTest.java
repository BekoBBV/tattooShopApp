package com.tattoostudio.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.support.ApiTest;

class BookingApiTest extends ApiTest {

    // --- The public studio ---

    @Test
    void artistsAndFlashMatchTheFrontendContract() throws Exception {
        mvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Kai"))
                .andExpect(jsonPath("$[0].slug").value("kai"))
                .andExpect(jsonPath("$[0].style").value("Bold traditional colour"))
                .andExpect(jsonPath("$[0].hourlyRate").value(90))
                .andExpect(jsonPath("$[0].dayRate").value(550))
                .andExpect(jsonPath("$[0].weeklyHours.length()").value(6))
                .andExpect(jsonPath("$[0].weeklyHours[0].start").value("11:00"))
                .andExpect(jsonPath("$[0].averageRating").value(5.0))
                .andExpect(jsonPath("$[0].reviewCount").value(1));
        mvc.perform(get("/api/artists/kai")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Kai"));
        mvc.perform(get("/api/artists/kai/works")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/artists/nobody")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ARTIST_NOT_FOUND"));

        mvc.perform(get("/api/flash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].name").value("Lucky koi"))
                .andExpect(jsonPath("$[0].artistName").value("Kai"))
                .andExpect(jsonPath("$[0].widthCm").value(6))
                .andExpect(jsonPath("$[0].heightCm").value(8))
                .andExpect(jsonPath("$[0].size").value("SMALL"))
                .andExpect(jsonPath("$[0].durationMinutes").value(90))
                .andExpect(jsonPath("$[0].price").value(140))
                .andExpect(jsonPath("$[0].deposit").value(30))
                .andExpect(jsonPath("$[0].bookable").value(true));
        mvc.perform(get("/api/flash").param("artistId", artist("Nika").getId()))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/flash").param("size", "LARGE"))
                .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("Katana and smoke",
                        "Cherry blossom branch")));
    }

    @Test
    void availabilityForAFlashDesignOrAConsultation() throws Exception {
        mvc.perform(get("/api/availability").param("flashId", flash("Small dragon").getId())
                        .param("from", MONDAY.toString()).param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistId").value(artist("Kai").getId()))
                .andExpect(jsonPath("$.durationMinutes").value(120))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].closed").value(true)) // Monday
                .andExpect(jsonPath("$.days[0].slots.length()").value(0))
                .andExpect(jsonPath("$.days[1].closed").value(false))
                .andExpect(jsonPath("$.days[1].slots[0].time").value("11:00"))
                .andExpect(jsonPath("$.days[1].slots[1].time").value("11:30"));
        mvc.perform(get("/api/availability").param("artistId", artist("Nika").getId()).param("from", TUESDAY.toString())
                        .param("days", "1"))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.days[0].slots.length()").value(24));
        mvc.perform(get("/api/availability")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/availability").param("flashId", "nope")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLASH_NOT_FOUND"));
    }

    // --- Flash: book, pay, look up, cancel ---

    @Test
    void bookPayLookUpAndCancelAFlashDesign() throws Exception {
        String body = bookFlash("Small dragon", TUESDAY, "14:00");
        String code = JsonPath.read(body, "$.code");
        assertThat((String) JsonPath.read(body, "$.status")).isEqualTo("PENDING");
        assertThat((String) JsonPath.read(body, "$.phoneMasked")).endsWith("456");

        // The design is off the sheet while it is held
        mvc.perform(get("/api/flash")).andExpect(jsonPath("$[*].name", not(hasItem("Small dragon"))));
        mvc.perform(get("/api/flash/" + flash("Small dragon").getId())).andExpect(jsonPath("$.bookable").value(false));

        String lookup = "{\"code\":\"%s\",\"phone\":\"+359 888 123 456\"}".formatted(code);
        String checkout = JsonPath.read(postJson("/api/bookings/deposit", lookup).andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(30))
                .andReturn().getResponse().getContentAsString(), "$.checkoutUrl");
        assertThat(checkout).startsWith("/api/payments/demo/");

        // The test payment page, then "Pay"
        mvc.perform(get(checkout)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Small dragon")));
        mvc.perform(post(checkout).param("result", "paid"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/booking/confirmed?code=" + code));

        postJson("/api/bookings/lookup", lookup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.deposit.status").value("PAID"))
                .andExpect(jsonPath("$.deposit.method").value("ONLINE"))
                .andExpect(jsonPath("$.leftToPay").value(150))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.refundIfCancelledNow").value(false)); // 30 hours ahead

        postJson("/api/bookings/cancel", lookup)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.deposit.status").value("KEPT"));
        postJson("/api/bookings/cancel", lookup)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_ACTIVE"));
        // Back on the sheet
        mvc.perform(get("/api/flash")).andExpect(jsonPath("$[*].name", hasItem("Small dragon")));
    }

    @Test
    void cancellingOnThePaymentPageGoesBackToTheDepositStep() throws Exception {
        String code = JsonPath.read(bookFlash("Peony", TUESDAY, "14:00"), "$.code");
        Booking booking = bookings.findByCode(code).orElseThrow();
        mvc.perform(post("/api/payments/demo/" + booking.getPaymentId()).param("result", "cancel"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/book/deposit?code=" + code));
        assertThat(bookings.findByCode(code).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void invalidRequestListsTheBadFields() throws Exception {
        String json = flashJson(flash("Small dragon").getId(), TUESDAY, "14:00")
                .replace("\"adult\":true", "\"adult\":false").replace("ivan@example.com", "not-an-email");
        postJson("/api/bookings/flash", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.adult").value("Please confirm that you are 18 or older"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void underEighteenIsRefused() throws Exception {
        String json = flashJson(flash("Small dragon").getId(), TUESDAY, "14:00")
                .replace("1995-05-05", TUESDAY.minusYears(17).toString());
        postJson("/api/bookings/flash", json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNDER_AGE"))
                .andExpect(jsonPath("$.detail").value("You need to be 18 or older to get tattooed."));
    }

    @Test
    void takenTimeAndUnknownDesignHaveMachineReadableCodes() throws Exception {
        booking("Kai", TUESDAY, "15:00", BookingStatus.BOOKED);
        postJson("/api/bookings/flash", flashJson(flash("Small dragon").getId(), TUESDAY, "14:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        postJson("/api/bookings/flash", flashJson("unknown", TUESDAY, "14:00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLASH_NOT_FOUND"));
    }

    // --- Consultations ---

    @Test
    void aConsultationWithReferencePhotos() throws Exception {
        String body = mvc.perform(consultation(artist("Nika").getId(), TUESDAY, "12:30",
                        photo("photos", "koi.png"), photo("photos", "maple.png")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONSULTATION"))
                .andExpect(jsonPath("$.status").value("BOOKED"))
                .andExpect(jsonPath("$.deposit.status").value("NONE"))
                .andExpect(jsonPath("$.endTime").value("13:00"))
                .andExpect(jsonPath("$.consultation.idea").value("A koi swimming up through waves"))
                .andExpect(jsonPath("$.consultation.colour").value("FULL_COLOUR"))
                .andExpect(jsonPath("$.consultation.photos.length()").value(2))
                .andExpect(jsonPath("$.consultation.photos[0].url", startsWith("/uploads/references/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.consultation.photos[0].url");
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void aConsultationNeedsItsJsonPart() throws Exception {
        mvc.perform(multipart("/api/bookings/consultation").file(photo("photos", "koi.png")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void notAPictureIsRefused() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("photos", "virus.png", "image/png", "MZ not a picture".getBytes());
        mvc.perform(consultation(artist("Nika").getId(), TUESDAY, "12:30", fake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        assertThat(bookings.count()).isZero();
    }

    // --- Reviews and messages ---

    @Test
    void guestsReviewFinishedAppointmentsWithCodeAndPhone() throws Exception {
        Booking done = booking("Kai", MONDAY.minusDays(1), "14:00", BookingStatus.DONE);
        String review = "{\"code\":\"%s\",\"phone\":\"0888123456\",\"rating\":5,\"text\":\"Bright and solid\"}"
                .formatted(done.getCode());
        postJson("/api/bookings/review", review).andExpect(status().isCreated());
        postJson("/api/bookings/review", review).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        // A new review waits for approval, so the public count is unchanged
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.count").value(3));
        mvc.perform(get("/api/reviews").param("artistId", artist("Kai").getId()))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.reviews[0].name").value("Petar"));
    }

    @Test
    void messagesCanBeSentWithOrWithoutPhotos() throws Exception {
        postJson("/api/messages", "{\"name\":\"Test\",\"contact\":\"0888 123 456\",\"text\":\"Can you cover an old tattoo?\"}")
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/messages")
                        .file(new MockMultipartFile("message", "", MediaType.APPLICATION_JSON_VALUE,
                                "{\"name\":\"Ivana\",\"contact\":\"ivana@example.com\",\"text\":\"Cover-up?\"}".getBytes()))
                        .file(photo("photos", "shoulder.png"))
                        .with(fromIp(visitor)))
                .andExpect(status().isCreated());
        assertThat(messages.findAll()).anySatisfy(m -> assertThat(m.getPhotos()).hasSize(1));
    }

    @Test
    void reviewsAndMessagesAreLimitedPerVisitor() throws Exception {
        String message = "{\"name\":\"Spam\",\"contact\":\"x\",\"text\":\"Hi\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/messages").with(fromIp("10.9.0.9")).contentType(MediaType.APPLICATION_JSON).content(message))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/messages").with(fromIp("10.9.0.9")).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SUBMISSIONS"));
        // Another visitor is not affected
        mvc.perform(post("/api/messages").with(fromIp("10.9.0.10")).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isCreated());

        String review = "{\"code\":\"NONE-00\",\"phone\":\"0888123456\",\"rating\":5,\"text\":\"Great\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/bookings/review").with(fromIp("10.9.0.7")).contentType(MediaType.APPLICATION_JSON)
                    .content(review)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/bookings/review").with(fromIp("10.9.0.7")).contentType(MediaType.APPLICATION_JSON)
                .content(review)).andExpect(status().isTooManyRequests());
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
