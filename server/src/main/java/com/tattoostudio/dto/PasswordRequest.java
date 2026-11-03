package com.tattoostudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new password set by the admin. */
public record PasswordRequest(@NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters") String password) {
}
