package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Review;

/** An approved review as shown on the site: the customer's first name, the artist, rating and text. */
public record ReviewDto(String id, String artistId, String name, int rating, String text, Instant createdAt) {

    public static ReviewDto from(Review r) {
        return new ReviewDto(r.getId(), r.getArtistId(), r.getName(), r.getRating(), r.getText(), r.getCreatedAt());
    }
}
