package com.tattoostudio.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signs and checks staff login tokens (JWT, HMAC-SHA256) with a secret from
 * app.jwt.secret in secrets.properties. Without one, a random secret is used, which
 * means everyone has to sign in again after a server restart.
 */
@Configuration
public class JwtConfig {

    public static final String ISSUER = "tattoo-studio";
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final SecretKey key;

    public JwtConfig(@Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.require-secret:false}") boolean requireSecret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32 && requireSecret) {
            // Production: a random secret would silently sign everyone out on every restart
            throw new IllegalStateException("Set app.jwt.secret (APP_JWT_SECRET) to at least 32 characters");
        }
        if (bytes.length < 32) {
            if (!secret.isEmpty()) {
                log.warn("app.jwt.secret is shorter than 32 characters; using a random secret instead");
            } else {
                log.warn("app.jwt.secret is not set; using a random secret (staff must sign in again after a restart)");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(Clock clock, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator expiry = new JwtTimestampValidator();
        expiry.setClock(clock); // same clock as the rest of the app (tests control it)
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(expiry, new JwtIssuerValidator(ISSUER),
                loginStillValid(users)));
        return decoder;
    }

    /**
     * A token only works while its login (found by the token's account id) exists, is active, still
     * has the username, role and artist written in the token, and has not changed its password since
     * the token was issued. So resetting a password, changing someone's rights or artist, or deleting
     * a login signs out at once, and a new account that reuses a deleted one's email never inherits
     * the old tokens.
     */
    private static OAuth2TokenValidator<Jwt> loginStillValid(UserRepository users) {
        OAuth2Error signInAgain = new OAuth2Error("invalid_token", "Please sign in again", null);
        return jwt -> {
            String uid = jwt.getClaimAsString("uid");
            User user = uid == null ? null : users.findById(uid).orElse(null);
            Instant issued = jwt.getIssuedAt();
            boolean valid = user != null && user.isActive()
                    && user.getUsername().equals(jwt.getSubject())
                    && user.getRole().name().equals(jwt.getClaimAsString("role"))
                    && Objects.equals(user.getArtistId(), jwt.getClaimAsString("artistId"))
                    && (user.getPasswordChangedAt() == null
                            || (issued != null && !issued.isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS))));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(signInAgain);
        };
    }
}
