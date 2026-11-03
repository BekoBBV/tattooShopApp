package com.tattoostudio.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.Message;
import com.tattoostudio.support.ApiTest;
import com.jayway.jsonpath.JsonPath;

/** Paging, sorting and search on every list, and who may use them (guests: plain lists only). */
class ListApiTest extends ApiTest {

    private String customer() throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(visitor);
                    return request;
                })
                .content("""
                        {"name":"Lia","phone":"0888 000 111","email":"test-lia@example.com","password":"customer-password"}
                        """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    private Message message(String name, String text, boolean read) {
        Message m = new Message();
        m.setName(name);
        m.setContact(name.toLowerCase() + "@example.com");
        m.setText(text);
        m.setRead(read);
        m.setCreatedAt(clock.instant());
        return m;
    }

    private MockHttpServletRequestBuilder as(String auth, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", auth);
    }

    @Test
    void guestsSeeThePlainListsButOnlySignedInVisitorsSearch() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        for (String path : new String[] { "/api/services/search", "/api/artists/search", "/api/reviews/search" }) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        // Any role may search: customers and staff alike
        mvc.perform(as(adminBearer(), get("/api/services/search"))).andExpect(status().isOk());
    }

    @Test
    void servicesPageSortAndFilterByTextAndPrice() throws Exception {
        String lia = customer();
        mvc.perform(as(lia, get("/api/services/search").param("sort", "price,desc").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Cut + Beard")) // 46 €
                .andExpect(jsonPath("$.items[1].name").value("Skin Fade")) // 34 €
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(as(lia, get("/api/services/search").param("sort", "price,desc").param("size", "2").param("page", "2")))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Line-Up")); // the cheapest is last

        // Two criteria at once: text and price range
        mvc.perform(as(lia, get("/api/services/search").param("q", "BEARD").param("maxPrice", "30")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Beard Sculpt"));
        // Text is taken literally, not as a pattern
        mvc.perform(as(lia, get("/api/services/search").param("q", ".*")))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void artistsFilterByTextAndWorkingDay() throws Exception {
        String lia = customer();
        mvc.perform(as(lia, get("/api/artists/search").param("day", "SATURDAY").param("sort", "name,asc")))
                .andExpect(jsonPath("$.totalItems").value(5)) // Cruz has Saturdays off
                .andExpect(jsonPath("$.items[0].name").value("Jax Monroe"))
                .andExpect(jsonPath("$.items[?(@.name == 'Cruz Lee')]").isEmpty());
        mvc.perform(as(lia, get("/api/artists/search").param("q", "fade")))
                .andExpect(jsonPath("$.items[*].name").value(containsInAnyOrder("Jax Monroe", "Rook Davis")));
    }

    @Test
    void reviewsFilterByStarsAndText() throws Exception {
        String lia = customer();
        mvc.perform(as(lia, get("/api/reviews/search").param("minRating", "5")))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(as(lia, get("/api/reviews/search").param("q", "booking").param("sort", "rating,asc")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Theo R."));
    }

    @Test
    void staffBookingsSearchByPhoneAsTypedAndSort() throws Exception {
        booking("Nia", TUESDAY, "10:00", BookingStatus.BOOKED);
        Booking ana = booking("Nia", TUESDAY, "12:00", BookingStatus.BOOKED);
        ana.setCustomerName("Ana Dimitrova");
        ana.setPhone("+359877000222");
        ana.setEmail("ana@example.com");
        bookings.save(ana);

        String admin = adminBearer();
        mvc.perform(as(admin, get("/api/admin/bookings").param("q", "0877 000 222")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].customerName").value("Ana Dimitrova"));
        mvc.perform(as(admin, get("/api/admin/bookings").param("sort", "customer,desc")))
                .andExpect(jsonPath("$.items[0].customerName").value("Ivan Petrov"))
                .andExpect(jsonPath("$.items[1].customerName").value("Ana Dimitrova"));
        mvc.perform(as(artistBearer("Nia"), get("/api/staff/bookings").param("q", "ivan").param("status", "BOOKED")))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void badPagingAndSortAreRefused() throws Exception {
        String admin = adminBearer();
        mvc.perform(as(admin, get("/api/admin/bookings").param("sort", "password,asc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(admin, get("/api/admin/messages").param("size", "501")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(as(admin, get("/api/admin/reviews").param("page", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void messagesAreNumberedPerContactAndRememberWhenTheyWereRead() throws Exception {
        for (String contact : new String[] { "vera@example.com", "VERA@example.com", "0888 123 456", "+359888123456" }) {
            mvc.perform(post("/api/messages").contentType(MediaType.APPLICATION_JSON)
                    .with(request -> {
                        request.setRemoteAddr(visitor);
                        return request;
                    })
                    .content("{\"name\":\"Vera\",\"contact\":\"%s\",\"text\":\"Hi\"}".formatted(contact)))
                    .andExpect(status().isCreated());
        }
        String admin = adminBearer();
        String body = mvc.perform(as(admin, get("/api/admin/messages").param("sort", "date,asc")))
                .andExpect(jsonPath("$.items[0].senderMessageNumber").value(1))
                .andExpect(jsonPath("$.items[1].senderMessageNumber").value(2)) // same email, other case
                .andExpect(jsonPath("$.items[2].senderMessageNumber").value(1))
                .andExpect(jsonPath("$.items[3].senderMessageNumber").value(2)) // same phone, other format
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.items[0].id");
        mvc.perform(as(admin, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/admin/messages/" + id))
                .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isString());
    }

    @Test
    void adminInboxAndCatalogFilters() throws Exception {
        messages.save(message("Boris", "Do you do kids cuts?", true));
        messages.save(message("Vera", "Parking nearby?", false));
        String admin = adminBearer();
        mvc.perform(as(admin, get("/api/admin/messages").param("read", "false")))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Vera"));
        mvc.perform(as(admin, get("/api/admin/messages").param("q", "kids").param("read", "true")))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(as(admin, get("/api/admin/services").param("active", "true").param("minPrice", "30")))
                .andExpect(jsonPath("$.totalItems").value(2)); // Skin Fade, Cut + Beard
        mvc.perform(as(admin, get("/api/admin/artists").param("day", "TUESDAY").param("active", "true")))
                .andExpect(jsonPath("$.totalItems").value(5)); // Jax has Tuesdays off
    }
}
