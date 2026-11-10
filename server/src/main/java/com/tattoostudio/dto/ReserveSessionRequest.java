package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An artist reserving time for a custom piece. The customer is picked from the artist's earlier
 * consultations and sessions ({@code customerBookingId}, see GET /staff/customers).
 */
public record ReserveSessionRequest(
        @NotBlank String customerBookingId,
        @NotNull @Schema(example = "2026-11-14") LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "11:00") LocalTime start,
        @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "15:00") LocalTime end,
        @NotBlank @Size(max = 100) @Schema(example = "Back piece, session 1 of 3") String description,
        @NotNull @DecimalMin("0") @DecimalMax("20000") @Schema(example = "480") BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("20000") @Schema(example = "100") BigDecimal deposit) {
}
