package com.tattoostudio.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** Integration tests through HTTP, with helpers for staff logins (all test logins start with "test-"). */
@AutoConfigureMockMvc
public abstract class ApiTest extends IntegrationTest {

    protected static final String ADMIN_PASSWORD = "test-admin-password";
    protected static final String ARTIST_PASSWORD = "artist-password";
    private static final AtomicInteger TEST_CODES = new AtomicInteger();
    private static final AtomicInteger TEST_VISITORS = new AtomicInteger();

    /** Each test signs in from its own IP address, so failed logins of other tests never add up. */
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

    /** Creates a login "test-<firstname>" for a seeded artist, signs in and returns the header value. */
    protected String artistBearer(String firstName) throws Exception {
        String username = "test-" + firstName.toLowerCase();
        createArtistLogin(username, firstName);
        return bearer(username, ARTIST_PASSWORD);
    }

    protected void createArtistLogin(String username, String firstName) {
        users.save(new User(username, passwordEncoder.encode(ARTIST_PASSWORD), Role.ARTIST, artist(firstName).getId()));
    }

    /** Saves a one-hour booking directly, skipping the booking rules (e.g. to create past bookings). */
    protected Booking booking(String artistFirstName, LocalDate date, String time, BookingStatus status) {
        Booking b = new Booking();
        b.setCode("FS-T%05d".formatted(TEST_CODES.incrementAndGet())); // unique in every test run
        b.setServiceId(service("Street Cut").getId());
        b.setServiceName("Street Cut");
        b.setPriceAtBooking(service("Street Cut").getPrice());
        b.setArtistId(artist(artistFirstName).getId());
        b.setStartsAt(at(date, time));
        b.setEndsAt(at(date, time).plusSeconds(3600));
        b.setSlots(List.of(at(date, time)));
        b.setStatus(status);
        b.setCustomerName("Ivan Petrov");
        b.setPhone("+359888123456");
        b.setEmail("ivan@example.com");
        b.setNotes("Short on the sides");
        b.setCreatedAt(clock.instant());
        return bookings.save(b);
    }
}
