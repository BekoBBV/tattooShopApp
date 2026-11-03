package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Review;

public record ReviewDto(String id, String name, int rating, String text, Instant createdAt) {

    public static ReviewDto from(Review r) {
        return new ReviewDto(r.getId(), r.getName(), r.getRating(), r.getText(), r.getCreatedAt());
    }
}
