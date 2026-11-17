package com.tattoostudio.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.tattoostudio.support.ApiTest;

/** The React site is served next to the API (src/test/resources/static/index.html stands in for the build). */
class SpaConfigTest extends ApiTest {

    @Test
    void pageAddressesGetTheReactApp() throws Exception {
        // "/" is Spring Boot's welcome page: an internal forward to index.html
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        for (String page : new String[] { "/book/time", "/admin/artists", "/staff/hours" }) {
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("test index")));
        }
    }

    @Test
    void missingFilesStay404() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void theApiIsClosedByDefault() throws Exception {
        // An /api address that is on no list is refused, whoever asks: not public by accident, not even for the admin
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-such-endpoint").header("Authorization", adminBearer())).andExpect(status().isForbidden());
        // The public lists stay open, and their write side stays closed to guests
        mvc.perform(get("/api/artists")).andExpect(status().isOk());
        mvc.perform(get("/api/reviews")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/flash/x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pagesAndApiAnswersCarryTheContentSecurityPolicy() throws Exception {
        mvc.perform(get("/book/time"))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY));
        mvc.perform(get("/api/flash"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
    }

    @Test
    void theApiStillWins() throws Exception {
        mvc.perform(get("/api/flash")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
