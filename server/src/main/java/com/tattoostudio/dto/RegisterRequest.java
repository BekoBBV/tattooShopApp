package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A customer creating an account. The email address is also what they sign in with. */
public record RegisterRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Alex Morgan") String name,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @NotBlank @Email @Size(max = 120) @Schema(example = "alex@example.com") String email,
        @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters") String password) {
}
