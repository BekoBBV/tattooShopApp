package com.tattoostudio.dto;

import java.util.List;

/** Approved reviews plus the average rating for the "what clients say" section. */
public record ReviewSummaryDto(double averageRating, int count, List<ReviewDto> reviews) {
}
