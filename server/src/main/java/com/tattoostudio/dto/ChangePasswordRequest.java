package com.tattoostudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A signed-in customer choosing a new password (the current one confirms it is really them). */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters") String newPassword) {
}
