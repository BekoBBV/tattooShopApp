package com.tattoostudio.controller;

import java.time.Clock;
import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.CreateReviewRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ReviewDto;
import com.tattoostudio.dto.ReviewSummaryDto;
import com.tattoostudio.model.Review;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.service.InboxService;
import com.tattoostudio.service.SubmissionLimiter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews", description = "Customer reviews (public)")
public class ReviewController {

    private final ReviewRepository reviews;
    private final InboxService inbox;
    private final SubmissionLimiter limiter;
    private final Clock clock;

    public ReviewController(ReviewRepository reviews, InboxService inbox, SubmissionLimiter limiter, Clock clock) {
        this.reviews = reviews;
        this.inbox = inbox;
        this.limiter = limiter;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Approved reviews, newest first, with the average rating")
    public ReviewSummaryDto list() {
        List<ReviewDto> approved = reviews.findByApprovedTrueOrderByCreatedAtDesc().stream().map(ReviewDto::from).toList();
        double average = approved.stream().mapToInt(ReviewDto::rating).average().orElse(0);
        // One decimal place, e.g. 4.7
        return new ReviewSummaryDto(Math.round(average * 10) / 10.0, approved.size(), approved);
    }

    @GetMapping("/search")
    @Operation(summary = "Search approved reviews (signed in)",
            description = "Paging, sorting (date, rating, name) and search by text and minimum stars.")
    @SecurityRequirement(name = "bearer")
    public PageDto<ReviewDto> search(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minRating) {
        return inbox.searchReviews(list.text(), true, minRating, list)
                .map(page -> page.stream().map(ReviewDto::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Leave a review", description = "It stays hidden until the admin approves it.")
    public void create(@Valid @RequestBody CreateReviewRequest request, HttpServletRequest http) {
        limiter.checkReview(http.getRemoteAddr());
        reviews.save(new Review(request.name().trim(), request.rating(), request.text().trim(), false, clock.instant()));
    }
}
