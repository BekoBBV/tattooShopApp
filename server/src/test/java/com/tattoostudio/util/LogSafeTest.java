package com.tattoostudio.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;

/** The server log names people without personal data. */
class LogSafeTest {

    @Test
    void emailsAreMaskedAndStaffNamesKept() {
        assertThat(LogSafe.mask("ivan.petrov@example.com")).isEqualTo("iv***@example.com");
        assertThat(LogSafe.mask("a@example.com")).isEqualTo("a***@example.com");
        assertThat(LogSafe.mask("kai")).isEqualTo("kai");
        assertThat(LogSafe.mask(null)).isEqualTo("(none)");
    }

    @Test
    void customersAreNamedByTheirAccountIdNotTheirEmail() {
        User customer = new User("ivan@example.com", "hash", Role.USER, null);
        ReflectionTestUtils.setField(customer, "id", "u1");
        assertThat(LogSafe.account(customer)).isEqualTo("customer u1").doesNotContain("@");
        assertThat(LogSafe.account(new User("kai", "hash", Role.ARTIST, "a1"))).isEqualTo("'kai'");
    }
}
