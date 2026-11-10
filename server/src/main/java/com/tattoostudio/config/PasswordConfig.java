package com.tattoostudio.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;

/**
 * Passwords are kept as BCrypt hashes. BCrypt reads at most 72 bytes, and refuses to hash a longer
 * password, so a longer one is turned down with a clear 400 instead of a server error. The forms
 * allow 72 characters; letters outside plain Latin take 2 to 4 bytes each, which this catches.
 */
@Configuration
public class PasswordConfig {

    static final int MAX_BYTES = 72;

    @Bean
    PasswordEncoder passwordEncoder() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        return new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                if (tooLong(raw)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                            "This password is too long. Please choose a shorter one.");
                }
                return bcrypt.encode(raw);
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                // No stored password is longer, so a longer one is simply wrong
                return raw != null && !tooLong(raw) && bcrypt.matches(raw, encoded);
            }
        };
    }

    private static boolean tooLong(CharSequence raw) {
        return raw != null && raw.toString().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
