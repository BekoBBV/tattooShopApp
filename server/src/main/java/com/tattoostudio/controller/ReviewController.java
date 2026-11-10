package com.tattoostudio.controller;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.ReviewDto;
import com.tattoostudio.dto.ReviewSummaryDto;
import com.tattoostudio.model.Review;
import com.tattoostudio.repository.ReviewRepository;
import com.tattoostudio.service.CatalogService;
import com.tattoostudio.service.InboxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Approved customer reviews (public). Customers write them about a finished appointment: see
 * POST /bookings/review and POST /account/bookings/{code}/review.
 */
@RestController
@RequestMapping("/api/reviews")
@Tag(name = "Reviews", description = "Customer reviews (public)")
public class ReviewController {

    private final ReviewRepository reviews;
    private final InboxService inbox;

    public ReviewController(ReviewRepository reviews, InboxService inbox) {
        this.reviews = reviews;
        this.inbox = inbox;
    }

    @GetMapping
    @Operation(summary = "Approved reviews, newest first, with the average rating",
            description = "Give artistId for one artist's reviews (their page).")
    public ReviewSummaryDto list(@Parameter(description = "Artist id") @RequestParam(required = false) String artistId) {
        List<Review> approved = artistId == null || artistId.isBlank()
                ? reviews.findByApprovedTrueOrderByCreatedAtDesc()
                : reviews.findByArtistIdAndApprovedTrueOrderByCreatedAtDesc(artistId);
        Double average = CatalogService.average(approved);
        return new ReviewSummaryDto(average == null ? 0 : average, approved.size(),
                approved.stream().map(ReviewDto::from).toList());
    }

    @GetMapping("/search")
    @Operation(summary = "Search approved reviews (signed in)",
            description = "Paging, sorting (date, rating, name) and search by text, artist and minimum stars.")
    @SecurityRequirement(name = "bearer")
    public PageDto<ReviewDto> search(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) @Min(1) @Max(5) Integer minRating,
            @RequestParam(required = false) String artistId) {
        return inbox.searchReviews(list.text(), true, minRating, artistId, list)
                .map(page -> page.stream().map(ReviewDto::from).toList());
    }
}
