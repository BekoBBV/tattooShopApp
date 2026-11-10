package com.tattoostudio.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.BookingLookupRequest;
import com.tattoostudio.dto.CreateConsultationRequest;
import com.tattoostudio.dto.CreateFlashBookingRequest;
import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.GuestReviewRequest;
import com.tattoostudio.dto.PaymentStartDto;
import com.tattoostudio.model.Booking;
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

    @PostMapping("/flash")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a flash design",
            description = "Holds the time and the design for 15 minutes (status PENDING, deposit WAITING, deposit.dueAt) "
                    + "while the deposit is paid: then call POST /bookings/deposit. Returns the booking with its code "
                    + "(e.g. K7Q2-M9). Signed in as a customer (Bearer token)? It also goes to My account; a sign-in "
                    + "that no longer works books as a guest.")
    public BookingDto bookFlash(@Valid @RequestBody CreateFlashBookingRequest request, HttpServletRequest http) {
        limiter.checkBooking(http.getRemoteAddr());
        BookingDto booking = bookingService.bookFlash(request, customerId(http));
        limiter.recordBooking(http.getRemoteAddr());
        return booking;
    }

    @PostMapping(value = "/consultation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a free consultation",
            description = "A multipart form: the part \"booking\" is the JSON request (Content-Type application/json), "
                    + "plus up to 5 \"photos\" parts (JPG, PNG or HEIC, 10 MB each). Confirmed at once, no deposit.")
    public BookingDto bookConsultation(@Valid @RequestPart("booking") CreateConsultationRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos, HttpServletRequest http) {
        limiter.checkBooking(http.getRemoteAddr());
        BookingDto booking = bookingService.bookConsultation(request, photos, customerId(http));
        limiter.recordBooking(http.getRemoteAddr());
        return booking;
    }

    /**
     * The signed-in customer's account id, or null for a guest. Security skips the token on the booking
     * endpoints (see SecurityConfig), so it is checked here, once; one that no longer works counts as
     * no sign-in.
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
        return bookingService.toDto(find(request, http));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel a booking by code + phone number",
            description = "Possible until the appointment starts. A paid deposit is refunded when that is at least "
                    + "48 hours ahead (refundIfCancelledNow), otherwise the studio keeps it.")
    public BookingDto cancel(@Valid @RequestBody BookingLookupRequest request, HttpServletRequest http) {
        return bookingService.toDto(bookingService.cancel(find(request, http), false));
    }

    @PostMapping("/deposit")
    @Operation(summary = "Pay the deposit of a booking (code + phone number)",
            description = "Returns the payment page to send the customer to. When the payment is done, the customer "
                    + "comes back to /booking/confirmed?code=… (or /manage?code=… if the time was lost meanwhile and "
                    + "the money refunded).")
    public PaymentStartDto deposit(@Valid @RequestBody BookingLookupRequest request, HttpServletRequest http) {
        return bookingService.startDeposit(find(request, http));
    }

    @PostMapping("/review")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review a finished appointment (code + phone number)",
            description = "Shown on the artist's page after the admin approves it. Customers with an account use "
                    + "POST /account/bookings/{code}/review.")
    public void review(@Valid @RequestBody GuestReviewRequest request, HttpServletRequest http) {
        limiter.checkReview(http.getRemoteAddr());
        Booking booking = bookingService.findForCustomer(request.code(), request.phone(), http.getRemoteAddr());
        bookingService.review(booking, new CreateReviewRequest(request.rating(), request.text()));
    }

    private Booking find(BookingLookupRequest request, HttpServletRequest http) {
        return bookingService.findForCustomer(request.code(), request.phone(), http.getRemoteAddr());
    }
}
