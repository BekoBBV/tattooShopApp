package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Review;

/** A review as the admin sees it; {@code approved} is the "Show on site" switch. */
public record AdminReviewDto(String id, String artistId, String bookingId, String name, int rating, String text,
        Instant createdAt, boolean approved) {

    public static AdminReviewDto from(Review r) {
        return new AdminReviewDto(r.getId(), r.getArtistId(), r.getBookingId(), r.getName(), r.getRating(), r.getText(),
                r.getCreatedAt(), r.isApproved());
    }
}
