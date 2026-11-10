package com.tattoostudio.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A flash design as an artist (or the admin) adds or edits it. {@code artistId} is only read when the
 * admin adds a design for an artist; {@code available} is optional (null keeps the current value).
 */
public record FlashRequest(
        @Schema(description = "Admin only: whose design this is", nullable = true) String artistId,
        @NotBlank @Size(max = 60) @Schema(example = "Small dragon") String name,
        @Min(1) @Max(60) int widthCm,
        @Min(1) @Max(60) int heightCm,
        @Min(30) @Max(720) @Schema(description = "A multiple of 30", example = "120") int durationMinutes,
        @NotNull @DecimalMin("1") @DecimalMax("5000") @Schema(example = "180") BigDecimal price,
        Boolean available) {
}
