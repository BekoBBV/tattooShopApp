package com.tattoostudio.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tattoostudio.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Who may call what:
 * - public site API (services, availability, bookings, reviews, …): anyone
 * - /api/auth/login, /api/auth/register: anyone; /api/auth/me: anyone signed in
 * - /api/account/**: customers with an account (role USER)
 * - /api/services|artists|reviews/search: anyone signed in (guests get the plain lists)
 * - /api/staff/**: artists and the admin (Bearer token)
 * - /api/admin/**: the admin only
 */
@Configuration
public class SecurityConfig {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

    /** Where BookingController reads the customer's token itself (see bearerTokenResolver). */
    static final java.util.Set<String> BOOKING_ENDPOINTS = java.util.Set.of("/api/bookings/flash", "/api/bookings/consultation");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // stateless token API, no cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // The site loads scripts, styles and fonts only from itself, so injected script can't run
                // and steal a sign-in token; pictures may come from Cloudinary (https) or be previews of chosen files (blob:)
                // Booking codes travel in page addresses (/manage?code=…), so other sites never see the address,
                // and the site asks for no camera, microphone or location
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.SAME_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/staff/**").hasAnyRole("ARTIST", "ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/account/**").hasRole("USER")
                        // Searching, sorting and paging the public lists is for signed-in visitors (any role)
                        .requestMatchers(HttpMethod.GET, "/api/artists/search", "/api/reviews/search")
                        .authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .authenticationEntryPoint((req, res, e) -> problem(req, res, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again."))
                        .accessDeniedHandler((req, res, e) -> problem(req, res, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You don't have access to this.")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> problem(req, res, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again."))
                        .accessDeniedHandler((req, res, e) -> problem(req, res, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "You don't have access to this.")))
                .build();
    }

    /**
     * Reads the Bearer token, except on the public booking endpoint: BookingController checks the token
     * there itself (once), so a sign-in that no longer works (expired, password changed, account blocked
     * or deleted) simply books as a guest instead of getting 401. Everywhere else it is checked as usual.
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> "POST".equals(request.getMethod()) && BOOKING_ENDPOINTS.contains(request.getRequestURI())
                ? null
                : standard.resolve(request);
    }

    /** The token's "role" claim (ADMIN / ARTIST / USER) becomes ROLE_ADMIN / ROLE_ARTIST / ROLE_USER. */
    private static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    /** Same JSON "problem detail" shape as every other API error. */
    private static void problem(HttpServletRequest req, HttpServletResponse res, HttpStatus status, ErrorCode code,
            String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail.replace("\"", "'") + "\",\"instance\":\""
                + req.getRequestURI().replace("\"", "") + "\",\"code\":\"" + code.name() + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
