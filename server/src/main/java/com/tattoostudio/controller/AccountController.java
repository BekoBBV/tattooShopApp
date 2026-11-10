package com.tattoostudio.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.AccountDto;
import com.tattoostudio.dto.BookingDto;
import com.tattoostudio.dto.ChangePasswordRequest;
import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.DeleteAccountRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.LoginResponse;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.PaymentStartDto;
import com.tattoostudio.dto.UpdateAccountRequest;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.Booking;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.BookingType;
import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;
import com.tattoostudio.repository.UserRepository;
import com.tattoostudio.service.AuthService;
import com.tattoostudio.service.BookingService;
import com.tattoostudio.service.SubmissionLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** "My account" for signed-in customers (role USER): their details and their own bookings. */
@RestController
@RequestMapping("/api/account")
@Tag(name = "Customer account")
public class AccountController {

    private final UserRepository users;
    private final BookingService bookingService;
    private final AuthService auth;
    private final SubmissionLimiter limiter;

    public AccountController(UserRepository users, BookingService bookingService, AuthService auth,
            SubmissionLimiter limiter) {
        this.users = users;
        this.bookingService = bookingService;
        this.auth = auth;
        this.limiter = limiter;
    }

    @GetMapping
    @Operation(summary = "My details", description = "Name, email, phone and date of birth, e.g. to fill in the booking form.")
    public AccountDto me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.findById(requireCustomerId(jwt))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again."));
        return new AccountDto(user.getName(), user.getEmail(), user.getPhone(), user.getDateOfBirth(), user.getCreatedAt());
    }

    @PutMapping
    @Operation(summary = "Change my details", description = "Name, phone and email (the date of birth stays). The email "
            + "is the sign-in, so a new one must be free (409 CONFLICT); the answer is a new sign-in for this device "
            + "(same shape as sign in), and a changed email signs other devices out.")
    public LoginResponse update(@Valid @RequestBody UpdateAccountRequest request, @AuthenticationPrincipal Jwt jwt) {
        return auth.updateDetails(requireCustomerId(jwt), request);
    }

    @GetMapping("/bookings")
    @Operation(summary = "My bookings", description = "Bookings made while signed in, and sessions artists reserved for me. "
            + "when=UPCOMING (default, soonest first), PAST or ALL (latest first); filter by type (e.g. CONSULTATION for "
            + "My consultations) and status; search the title, idea or code; sort by date, title, type, price or status.")
    public PageDto<BookingDto> bookings(@AuthenticationPrincipal Jwt jwt, @Valid @ParameterObject ListQuery list,
            @RequestParam(defaultValue = "UPCOMING") BookingService.When when,
            @RequestParam(required = false) BookingType type,
            @RequestParam(required = false) BookingStatus status) {
        return bookingService.forAccount(requireCustomerId(jwt), when, type, status, list);
    }

    @PostMapping("/bookings/{code}/cancel")
    @Operation(summary = "Cancel one of my bookings", description = "Same rules as POST /bookings/cancel.")
    public BookingDto cancel(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        Booking booking = bookingService.findForAccount(code, requireCustomerId(jwt));
        return bookingService.toDto(bookingService.cancel(booking, false));
    }

    @PostMapping("/bookings/{code}/deposit")
    @Operation(summary = "Pay the deposit of one of my bookings", description = "Same as POST /bookings/deposit.")
    public PaymentStartDto deposit(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        return bookingService.startDeposit(bookingService.findForAccount(code, requireCustomerId(jwt)));
    }

    @PostMapping("/bookings/{code}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review one of my finished appointments", description = "Shown after the admin approves it.")
    public void review(@PathVariable String code, @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        limiter.checkReview(http.getRemoteAddr());
        bookingService.review(bookingService.findForAccount(code, requireCustomerId(jwt)), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change my password", description = "Needs the current password. Signs out other devices; "
            + "the answer is a new sign-in for this one (same shape as sign in).")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        return auth.changePassword(requireCustomerId(jwt), request.currentPassword(), request.newPassword());
    }

    @PostMapping("/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my account", description = "Needs the password. Name, email, phone and password are "
            + "removed; bookings stay for the studio (an upcoming one still works with its code + phone).")
    public void delete(@Valid @RequestBody DeleteAccountRequest request, @AuthenticationPrincipal Jwt jwt) {
        auth.deleteAccount(requireCustomerId(jwt), request.password());
    }

    /** The customer account id in a token, or null when the caller is a guest or staff. */
    static String customerId(Jwt jwt) {
        return jwt != null && Role.USER.name().equals(jwt.getClaimAsString("role")) ? jwt.getClaimAsString("uid") : null;
    }

    private static String requireCustomerId(Jwt jwt) {
        String id = customerId(jwt);
        if (id == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Please sign in again.");
        }
        return id;
    }
}
