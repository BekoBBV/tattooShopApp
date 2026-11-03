package com.tattoostudio.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.tattoostudio.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** The public API through real HTTP requests, the way the frontend calls it (see docs/api.md). */
@AutoConfigureMockMvc
class BookingApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void catalogMatchesTheFrontendContract() throws Exception {
        mvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Street Cut"))
                .andExpect(jsonPath("$[0].label").value("MD 01"))
                .andExpect(jsonPath("$[0].palette").value("blue"))
                .andExpect(jsonPath("$[0].durationMin").doesNotExist());

        mvc.perform(get("/api/artists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Jax Monroe"))
                .andExpect(jsonPath("$[0].handle").value("@jaxcuts"))
                .andExpect(jsonPath("$[0].accent").value("red"))
                .andExpect(jsonPath("$[0].weeklyHours[0].day").value("WEDNESDAY")) // Tuesday is his day off
                .andExpect(jsonPath("$[0].weeklyHours[0].start").value("10:00"));
    }

    @Test
    void sevenDaysOfAvailabilityInOneCall() throws Exception {
        String serviceId = service("Street Cut").getId();
        mvc.perform(get("/api/availability").param("serviceId", serviceId).param("from", MONDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artistId").doesNotExist())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[0].date").value(MONDAY.toString()))
                .andExpect(jsonPath("$.days[0].slots.length()").value(0)) // shop closed on Mondays
                .andExpect(jsonPath("$.days[1].slots[0].time").value("10:00"))
                .andExpect(jsonPath("$.days[1].slots[0].freeArtists").value(5));
    }

    @Test
    void bookLookUpAndCancel() throws Exception {
        String serviceId = service("Street Cut").getId();
        String created = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceId":"%s","artistId":null,"date":"%s","time":"10:00","customerName":"Ivan",
                 "phone":"0888 123 456","email":null,"notes":"Short on the sides"}
                """.formatted(serviceId, TUESDAY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.time").value("10:00"))
                .andExpect(jsonPath("$.price").value(28.0))
                .andExpect(jsonPath("$.notes").value("Short on the sides"))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.code");

        mvc.perform(post("/api/bookings/lookup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"phone\":\"+359888123456\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ivan"))
                .andExpect(jsonPath("$.cancellable").value(true));

        mvc.perform(post("/api/bookings/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\",\"phone\":\"0888123456\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void invalidRequestListsTheBadFields() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content("""
                {"serviceId":"x","date":"%s","time":"10:00","customerName":"","phone":"","email":"not-an-email",
                 "notes":"%s"}
                """.formatted(TUESDAY, "x".repeat(301))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.phone").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.notes").exists());
    }

    @Test
    void reviewsAndMessagesCanBeSent() throws Exception {
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"rating\":5,\"text\":\"Great cut\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"rating\":9,\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
        // A new review waits for approval, so the public count is unchanged
        mvc.perform(get("/api/reviews")).andExpect(jsonPath("$.count").value(3));

        mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"contact\":\"0888 123 456\",\"text\":\"Do you do kids' cuts?\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void reviewsAndMessagesAreLimitedPerVisitor() throws Exception {
        String review = "{\"name\":\"Spam\",\"rating\":5,\"text\":\"Great\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/reviews").with(fromIp("10.0.0.7")).contentType(MediaType.APPLICATION_JSON).content(review))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/reviews").with(fromIp("10.0.0.7")).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_SUBMISSIONS"));
        // Another visitor is not affected
        mvc.perform(post("/api/reviews").with(fromIp("10.0.0.8")).contentType(MediaType.APPLICATION_JSON).content(review))
                .andExpect(status().isCreated());

        String message = "{\"name\":\"Spam\",\"contact\":\"x\",\"text\":\"Hi\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/messages").with(fromIp("10.0.0.9")).contentType(MediaType.APPLICATION_JSON).content(message))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/messages").with(fromIp("10.0.0.9")).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isTooManyRequests());
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void unknownServiceIs404() throws Exception {
        mvc.perform(get("/api/availability").param("serviceId", "nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Service not found"));
    }

    @Test
    void takenTimeHasAMachineReadableCode() throws Exception {
        String body = """
                {"serviceId":"%s","artistId":"%s","date":"%s","time":"10:00","customerName":"A","phone":"%s"}
                """;
        String serviceId = service("Street Cut").getId();
        String niaId = artist("Nia").getId();
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(serviceId, niaId, TUESDAY, "0888111111")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(serviceId, niaId, TUESDAY, "0888222222")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
    }
}
