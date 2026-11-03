package com.tattoostudio.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.tattoostudio.support.ApiTest;

/** The "prod" profile: no API docs, and no start without a real token secret. */
@ActiveProfiles("prod") // on top of "test"
@TestPropertySource(properties = "app.jwt.secret=a-test-secret-that-is-long-enough-1234567890")
class ProdProfileTest extends ApiTest {

    @Test
    void apiDocsAreHidden() throws Exception {
        // No OpenAPI description any more: the address just gets the React app (its "page not found")
        mvc.perform(get("/v3/api-docs")).andExpect(content().string(not(containsString("openapi"))));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/services")).andExpect(status().isOk());
    }

    @Test
    void refusesToStartWithoutATokenSecret() {
        assertThatThrownBy(() -> new JwtConfig("", true)).hasMessageContaining("APP_JWT_SECRET");
        assertThatThrownBy(() -> new JwtConfig("too-short", true)).isInstanceOf(IllegalStateException.class);
    }
}
