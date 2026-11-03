package com.tattoostudio.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The admin editing a review (e.g. fixing a typo or removing a phone number someone posted). */
public record ReviewEditRequest(
        @NotBlank @Size(max = 60) String name,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 500) String text,
        @NotNull Boolean approved) {
}
