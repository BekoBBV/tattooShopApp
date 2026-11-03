package com.tattoostudio.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(
        @NotBlank String serviceId,
        @Schema(description = "Artist id, or null for any artist", nullable = true) String artistId,
        @NotNull @Schema(example = "2026-10-20") LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "14:00") LocalTime time,
        @NotBlank @Size(max = 60) String customerName,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @Email @Size(max = 120) @Schema(nullable = true) String email,
        @Size(max = 300) @Schema(nullable = true, example = "Keep some length on top.") String notes) {
}
