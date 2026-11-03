package com.tattoostudio.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.BookingLookupRequest;
import com.tattoostudio.dto.CreateBookingRequest;
import com.tattoostudio.service.BookingService;
import com.tattoostudio.service.SubmissionLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Booking")
public class BookingController {

    private final BookingService bookingService;
    private final JwtDecoder jwtDecoder;
    private final BearerTokenResolver bearer = new DefaultBearerTokenResolver();
    private final SubmissionLimiter limiter;

    public BookingController(BookingService bookingService, JwtDecoder jwtDecoder, SubmissionLimiter limiter) {
        this.bookingService = bookingService;
        this.jwtDecoder = jwtDecoder;
        this.limiter = limiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book an appointment",
            description = "Leave artistId empty for any artist. Returns the booking with its code (e.g. FS-7K3QXM). "
                    + "Signed in as a customer (Bearer token)? The booking is also added to My bookings; "
                    + "a sign-in that no longer works books as a guest.")
    public BookingDto create(@Valid @RequestBody CreateBookingRequest request, HttpServletRequest http) {
        limiter.checkBooking(http.getRemoteAddr());
        BookingDto booking = bookingService.create(request, customerId(http));
        limiter.recordBooking(http.getRemoteAddr());
        return booking;
    }

    /**
     * The signed-in customer's account id, or null for a guest. Security skips the token on this endpoint
     * (see SecurityConfig), so it is checked here, once; one that no longer works counts as no sign-in.
     */
    private String customerId(HttpServletRequest http) {
        try {
            String token = bearer.resolve(http);
            return token == null ? null : AccountController.customerId(jwtDecoder.decode(token));
        } catch (JwtException | AuthenticationException notValidAnyMore) {
            return null;
        }
    }

    // POST rather than GET so the phone number never ends up in URLs or server logs
    @PostMapping("/lookup")
    @Operation(summary = "Find a booking by code + phone number")
    public BookingDto lookup(@Valid @RequestBody BookingLookupRequest request, HttpServletRequest http) {
        return bookingService.lookup(request.code(), request.phone(), http.getRemoteAddr());
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel a booking by code + phone number",
            description = "Allowed until 2 hours before the appointment.")
    public BookingDto cancel(@Valid @RequestBody BookingLookupRequest request, HttpServletRequest http) {
        return bookingService.cancel(request.code(), request.phone(), http.getRemoteAddr());
    }
}
