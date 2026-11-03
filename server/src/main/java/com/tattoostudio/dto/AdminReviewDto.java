package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Review;

public record AdminReviewDto(String id, String name, int rating, String text, Instant createdAt, boolean approved) {

    public static AdminReviewDto from(Review r) {
        return new AdminReviewDto(r.getId(), r.getName(), r.getRating(), r.getText(), r.getCreatedAt(), r.isApproved());
    }
}
