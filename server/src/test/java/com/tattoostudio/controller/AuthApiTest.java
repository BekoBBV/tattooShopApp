package com.tattoostudio.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.tattoostudio.model.User;
import com.tattoostudio.support.ApiTest;

/** Staff sign-in, tokens and who may call which part of the API. */
class AuthApiTest extends ApiTest {

    @Test
    void adminSignsInAndSeesWhoTheyAre() throws Exception {
        login("Admin", "test-admin-password") // usernames are not case-sensitive
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.artistId").doesNotExist());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer("admin", "test-admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void artistTokenCarriesTheirArtistIdAndName() throws Exception {
        createArtistLogin("test-nika", "Nika");
        login("test-nika", "artist-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ARTIST"))
                .andExpect(jsonPath("$.user.artistId").value(artist("Nika").getId()))
                .andExpect(jsonPath("$.user.name").value("Nika")); // the login has no name: the artist's is shown
        mvc.perform(get("/api/auth/me").header("Authorization", bearer("test-nika", "artist-password")))
                .andExpect(jsonPath("$.name").value("Nika"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
        createArtistLogin("test-dario", "Dario");
        login("test-dario", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
        login("test-nobody", "whatever-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    void deactivatedLoginCannotSignIn() throws Exception {
        createArtistLogin("test-kai", "Kai");
        User user = users.findByUsername("test-kai").orElseThrow();
        user.setActive(false);
        users.save(user);
        login("test-kai", "artist-password").andExpect(status().isUnauthorized());
    }

    @Test
    void fiveWrongPasswordsBlockTheUsernameFor5Minutes() throws Exception {
        createArtistLogin("test-lock", "Nika");
        for (int i = 0; i < 5; i++) {
            login("test-lock", "wrong-password").andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while blocked
        login("test-lock", "artist-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

        // The block is per username, not per device: someone else is refused too...
        loginFrom("10.9.9.9", "test-lock", "artist-password").andExpect(status().isTooManyRequests());
        // ...but only for 5 minutes
        clock.advance(Duration.ofMinutes(4));
        login("test-lock", "artist-password").andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofMinutes(2));
        login("test-lock", "artist-password").andExpect(status().isOk());
    }

    @Test
    void tenFailedSignInsFromOneDeviceBlockItFor15Minutes() throws Exception {
        createArtistLogin("test-device", "Kai");
        // Trying one password on many usernames (never 5 on the same one)
        for (int i = 0; i < 10; i++) {
            login("test-guess-" + (i % 3), "wrong-password").andExpect(status().isUnauthorized());
        }
        login("test-device", "artist-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.detail").value("Too many failed sign-ins from this device. Please wait 15 minutes and try again."));
        // Other devices can still sign in
        loginFrom("10.9.9.8", "test-device", "artist-password").andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(16));
        login("test-device", "artist-password").andExpect(status().isOk());
    }

    @Test
    void staffRoutesNeedAValidToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tokensExpireAfter12Hours() throws Exception {
        String token = bearer("admin", "test-admin-password");
        clock.advance(Duration.ofHours(11));
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
        clock.advance(Duration.ofHours(2));
        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void artistsCannotUseAdminRoutes() throws Exception {
        createArtistLogin("test-dario", "Dario");
        String token = bearer("test-dario", "artist-password");
        mvc.perform(get("/api/admin/stats").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void publicSiteStaysOpen() throws Exception {
        mvc.perform(get("/api/flash")).andExpect(status().isOk());
    }
}
