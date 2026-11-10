package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A customer changing their details on My account. The email is also what they sign in with; the
 * date of birth can't be changed (the age check of the sign-up stays true).
 */
public record UpdateAccountRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Mira Petrova") String name,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone,
        @NotBlank @Email @Size(max = 120) @Schema(example = "mira@example.com") String email) {
}
