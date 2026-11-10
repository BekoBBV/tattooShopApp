package com.tattoostudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new password set by the admin. */
public record PasswordRequest(@NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password) {
}
