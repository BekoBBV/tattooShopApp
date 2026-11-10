package com.tattoostudio.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** A customer creating an account (18 or older). The email address is also what they sign in with. */
public record RegisterRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Mira Petrova") String name,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @NotBlank @Email @Size(max = 120) @Schema(example = "mira@example.com") String email,
        @NotNull @Past @Schema(example = "1998-03-12") LocalDate dateOfBirth,
        @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters") String password) {
}
