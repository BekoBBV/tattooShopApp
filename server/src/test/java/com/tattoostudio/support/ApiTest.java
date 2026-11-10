package com.tattoostudio.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.DepositStatus;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;

/** Integration tests through HTTP, with helpers for logins (all test logins start with "test-") and bookings. */
@AutoConfigureMockMvc
public abstract class ApiTest extends IntegrationTest {

    protected static final String ADMIN_PASSWORD = "test-admin-password";
    protected static final String ARTIST_PASSWORD = "artist-password";
    /** A tiny real PNG picture (1 × 1 pixel). */
    protected static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final AtomicInteger TEST_CODES = new AtomicInteger();
    private static final AtomicInteger TEST_VISITORS = new AtomicInteger();

    /** Each test calls from its own IP address, so attempt limits of other tests never add up. */
    protected String visitor;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void newVisitor() {
        int n = TEST_VISITORS.incrementAndGet();
        visitor = "10.0.%d.%d".formatted(n / 250, n % 250 + 1);
    }

    @AfterEach
    void removeTestLogins() {
        users.deleteAll(users.findAll().stream().filter(u -> u.getUsername().startsWith("test-")).toList());
    }

    protected ResultActions login(String username, String password) throws Exception {
        return loginFrom(visitor, username, password);
    }

    protected ResultActions loginFrom(String ip, String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    /** Signs in and returns the "Authorization" header value. */
    protected String bearer(String username, String password) throws Exception {
        String body = login(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.token");
    }

    protected String adminBearer() throws Exception {
        return bearer("admin", ADMIN_PASSWORD);
    }

    /** Creates a login "test-<name>" for a seeded artist, signs in and returns the header value. */
    protected String artistBearer(String name) throws Exception {
        String username = "test-" + name.toLowerCase();
        createArtistLogin(username, name);
        return bearer(username, ARTIST_PASSWORD);
    }

    protected void createArtistLogin(String username, String name) {
        users.save(new User(username, passwordEncoder.encode(ARTIST_PASSWORD), Role.ARTIST, artist(name).getId()));
    }

    /** POST as this test's visitor. */
    protected ResultActions postJson(String url, String json) throws Exception {
        return postJson(url, json, null);
    }

    protected ResultActions postJson(String url, String json, String bearer) throws Exception {
        var request = post(url).contentType(MediaType.APPLICATION_JSON).content(json).with(r -> {
            r.setRemoteAddr(visitor);
            return r;
        });
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        return mvc.perform(request);
    }

    /** The JSON for booking a flash design on {@code date} at {@code time} with the usual test customer. */
    protected String flashJson(String flashId, LocalDate date, String time) {
        return """
                {"flashId":"%s","date":"%s","time":"%s","customerName":"Ivan Petrov","phone":"0888 123 456",
                 "email":"ivan@example.com","dateOfBirth":"1995-05-05","adult":true,"consentForm":true,"depositPolicy":true}
                """.formatted(flashId, date, time);
    }

    /** Books a flash design over HTTP and returns the response body (status PENDING, deposit waiting). */
    protected String bookFlash(String flashName, LocalDate date, String time) throws Exception {
        return postJson("/api/bookings/flash", flashJson(flash(flashName).getId(), date, time))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    /** A multipart consultation request with the usual test customer and these photos. */
    protected MockMultipartHttpServletRequestBuilder consultation(String artistId, LocalDate date, String time,
            MockMultipartFile... photos) {
        String json = """
                {"artistId":"%s","date":"%s","time":"%s","customerName":"Ivan Petrov","phone":"0888 123 456",
                 "email":"ivan@example.com","dateOfBirth":"1995-05-05","idea":"A koi swimming up through waves",
                 "placement":"FOREARM","sizeCm":18,"colour":"FULL_COLOUR","adult":true,"consentForm":true}
                """.formatted(artistId, date, time);
        MockMultipartHttpServletRequestBuilder request = multipart("/api/bookings/consultation");
        request.file(new MockMultipartFile("booking", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes()));
        for (MockMultipartFile photo : photos) {
            request.file(photo);
        }
        request.with(r -> {
            r.setRemoteAddr(visitor);
            return r;
        });
        return request;
    }

    protected static MockMultipartFile photo(String partName, String fileName) {
        return new MockMultipartFile(partName, fileName, MediaType.IMAGE_PNG_VALUE, PNG);
    }

    /**
     * Saves a booking directly, skipping the booking rules (e.g. to create past bookings): a reserved
     * session of {@code minutes} for the usual test customer, taking its time plus a 30-minute break.
     */
    protected Booking booking(String artistName, LocalDate date, String time, BookingStatus status) {
        return booking(artistName, date, time, 60, BookingType.SESSION, status);
    }

    protected Booking booking(String artistName, LocalDate date, String time, int minutes, BookingType type,
            BookingStatus status) {
        Booking b = new Booking();
        b.setCode("T%03d-%02d".formatted(TEST_CODES.incrementAndGet() % 1000, TEST_CODES.get() % 100)); // unique per run
        b.setType(type);
        b.changeStatus(status);
        b.setTitle(type == BookingType.CONSULTATION ? null : "Test session");
        b.setPrice(type == BookingType.CONSULTATION ? null : new BigDecimal("100"));
        b.setDepositStatus(DepositStatus.NONE);
        b.setArtistId(artist(artistName).getId());
        Instant start = at(date, time);
        b.setStartsAt(start);
        b.setEndsAt(start.plus(Duration.ofMinutes(minutes)));
        List<Instant> slots = new ArrayList<>();
        for (Instant t = start; t.isBefore(b.getEndsAt().plus(Duration.ofMinutes(30))); t = t.plus(Duration.ofMinutes(30))) {
            slots.add(t);
        }
        b.setSlots(slots);
        b.setCustomerName("Ivan Petrov");
        b.setPhone("+359888123456");
        b.setEmail("ivan@example.com");
        b.setDateOfBirth(LocalDate.of(1995, 5, 5));
        if (type == BookingType.CONSULTATION) {
            b.setIdea("A koi swimming up through waves");
        }
        b.setCreatedAt(clock.instant());
        return bookings.insert(b);
    }
}
