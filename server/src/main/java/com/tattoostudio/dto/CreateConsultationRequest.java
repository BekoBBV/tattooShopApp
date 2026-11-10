package com.tattoostudio.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tattoostudio.model.ColourChoice;
import com.tattoostudio.model.Placement;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Booking a free consultation: the artist and time, the customer's details, the idea and the two
 * confirmations (no deposit). Reference photos come as separate parts of the same upload.
 */
public record CreateConsultationRequest(
        @NotBlank String artistId,
        @NotNull @Schema(example = "2026-11-20") LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "12:30") LocalTime time,
        @NotBlank @Size(max = 60) String customerName,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @NotBlank @Email @Size(max = 120) String email,
        @NotNull @Past @Schema(example = "1998-03-12") LocalDate dateOfBirth,
        @NotBlank @Size(max = 1000) @Schema(example = "A koi swimming up through waves on my forearm") String idea,
        @NotNull Placement placement,
        @Min(1) @Max(200) @Schema(description = "Approximate size in cm", nullable = true) Integer sizeCm,
        @NotNull ColourChoice colour,
        @AssertTrue(message = "Please confirm that you are 18 or older") boolean adult,
        @AssertTrue(message = "Please read and agree to the consent form") boolean consentForm) {
}
