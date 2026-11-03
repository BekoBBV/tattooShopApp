package com.tattoostudio.controller;

import java.time.Clock;
import java.time.LocalDate;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.AdminReviewDto;
import com.tattoostudio.dto.BookingUpdateRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.MessageDto;
import com.tattoostudio.dto.MessageUpdateRequest;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ReviewEditRequest;
import com.tattoostudio.dto.ReviewUpdateRequest;
import com.tattoostudio.dto.StaffBookingDto;
import com.tattoostudio.dto.StatsDto;
import com.tattoostudio.dto.StatusUpdateRequest;
import com.tattoostudio.error.ApiException;
import com.tattoostudio.error.ErrorCode;
import com.tattoostudio.model.BookingStatus;
import com.tattoostudio.model.Message;
import com.tattoostudio.model.Review;
import com.tattoostudio.repository.MessageRepository;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.service.InboxService;
import com.tattoostudio.service.StaffBookingService;
import com.tattoostudio.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Admin: every booking, stats, review moderation and the contact inbox. */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Bookings of all artists, stats, reviews and messages (admin only)")
@SecurityRequirement(name = "bearer")
public class AdminController {

    private final StaffBookingService staffBookings;
    private final StatsService stats;
    private final ReviewRepository reviews;
    private final MessageRepository messages;
    private final InboxService inbox;
    private final Clock clock;

    public AdminController(StaffBookingService staffBookings, StatsService stats, ReviewRepository reviews,
            MessageRepository messages, InboxService inbox, Clock clock) {
        this.staffBookings = staffBookings;
        this.stats = stats;
        this.reviews = reviews;
        this.messages = messages;
        this.inbox = inbox;
        this.clock = clock;
    }

    // ---- Bookings ----

    @GetMapping("/bookings")
    @Operation(summary = "All bookings", description = "Dates are inclusive. Default: the last 7 days and the next 30. "
            + "Search by customer, phone, email, code, service or notes; sort by date, service, customer, price or status.")
    public PageDto<StaffBookingDto> bookings(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) String artistId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) BookingStatus status) {
        return staffBookings.list(artistId == null || artistId.isBlank() ? null : artistId, from, to, status, list);
    }

    @PatchMapping("/bookings/{id}")
    @Operation(summary = "Mark any booking Done, No-show or Cancelled")
    public StaffBookingDto updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @Valid @RequestBody StatusUpdateRequest request) {
        return staffBookings.updateStatus(id, request.status(), StaffUser.from(jwt));
    }

    @PutMapping("/bookings/{id}")
    @Operation(summary = "Edit a booking's customer details and notes", description = "Time, artist and service stay. "
            + "Refused (409) once the details were removed for privacy.")
    public StaffBookingDto updateBooking(@PathVariable String id, @Valid @RequestBody BookingUpdateRequest request) {
        return staffBookings.updateDetails(id, request);
    }

    @DeleteMapping("/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a booking for good", description = "No email is sent; to tell the customer, cancel it instead.")
    public void deleteBooking(@PathVariable String id) {
        staffBookings.delete(id);
    }

    // ---- Stats ----

    @GetMapping("/stats")
    @Operation(summary = "Shop numbers", description = "Dates are inclusive. Default: this week (Monday to Sunday).")
    public StatsDto stats(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return stats.stats(from, to);
    }

    // ---- Reviews ----

    @GetMapping("/reviews")
    @Operation(summary = "Reviews, newest first", description = "status=pending or approved (all when left out), "
            + "minRating, search by name or text; sort by date, rating or name.")
    public PageDto<AdminReviewDto> reviews(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minRating) {
        Boolean approved = switch (status == null ? "" : status) {
            case "" -> null;
            case "pending" -> false;
            case "approved" -> true;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "status must be pending or approved");
        };
        return inbox.searchReviews(list.text(), approved, minRating, list)
                .map(page -> page.stream().map(AdminReviewDto::from).toList());
    }

    @PatchMapping("/reviews/{id}")
    @Operation(summary = "Approve (show on the site) or hide a review")
    public AdminReviewDto updateReview(@PathVariable String id, @Valid @RequestBody ReviewUpdateRequest request) {
        Review review = review(id);
        review.setApproved(request.approved());
        return AdminReviewDto.from(reviews.save(review));
    }

    @PutMapping("/reviews/{id}")
    @Operation(summary = "Edit a review", description = "Name, stars, text and whether it is shown.")
    public AdminReviewDto editReview(@PathVariable String id, @Valid @RequestBody ReviewEditRequest request) {
        Review review = review(id);
        review.setName(request.name().trim());
        review.setRating(request.rating());
        review.setText(request.text().trim());
        review.setApproved(request.approved());
        return AdminReviewDto.from(reviews.save(review));
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a review")
    public void deleteReview(@PathVariable String id) {
        reviews.delete(review(id));
    }

    // ---- Messages ----

    @GetMapping("/messages")
    @Operation(summary = "Contact messages, newest first (kept for 90 days)",
            description = "read=true / false (all when left out), search by name, contact or text; sort by date or name.")
    public PageDto<MessageDto> messages(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Boolean read) {
        return inbox.searchMessages(list.text(), read, list).map(page -> page.stream().map(MessageDto::from).toList());
    }

    @PatchMapping("/messages/{id}")
    @Operation(summary = "Mark a message read or unread")
    public MessageDto updateMessage(@PathVariable String id, @Valid @RequestBody MessageUpdateRequest request) {
        Message message = message(id);
        message.setRead(request.read());
        message.setReadAt(request.read() ? clock.instant() : null);
        return MessageDto.from(messages.save(message));
    }

    @DeleteMapping("/messages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a message")
    public void deleteMessage(@PathVariable String id) {
        messages.delete(message(id));
    }

    private Review review(String id) {
        return reviews.findById(id).orElseThrow(() -> notFound("Review"));
    }

    private Message message(String id) {
        return messages.findById(id).orElseThrow(() -> notFound("Message"));
    }

    private static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, what + " not found");
    }
}
