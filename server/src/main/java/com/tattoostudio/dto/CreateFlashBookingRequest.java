package com.tattoostudio.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Booking a flash design: the time, the customer's details and the three confirmations. */
public record CreateFlashBookingRequest(
        @NotBlank String flashId,
        @NotNull @Schema(example = "2026-11-20") LocalDate date,
        @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "14:00") LocalTime time,
        @NotBlank @Size(max = 60) String customerName,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @NotBlank @Email @Size(max = 120) String email,
        @NotNull @Past @Schema(example = "1998-03-12") LocalDate dateOfBirth,
        @Size(max = 300) @Schema(nullable = true) String notes,
        @AssertTrue(message = "Please confirm that you are 18 or older") boolean adult,
        @AssertTrue(message = "Please read and agree to the consent form") boolean consentForm,
        @AssertTrue(message = "Please confirm the deposit policy") boolean depositPolicy) {
}
