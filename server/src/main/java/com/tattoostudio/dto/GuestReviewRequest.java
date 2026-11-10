package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A guest reviewing a finished appointment, found by its code and phone number. */
public record GuestReviewRequest(
        @NotBlank @Size(max = 20) @Schema(example = "K7Q2-M9") String code,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 500) String text) {
}
