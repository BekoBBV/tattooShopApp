package com.tattoostudio.dto;

import com.tattoostudio.model.Role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Creates a login (POST, password required) or changes its rights (PUT, password ignored).
 * Customers (USER) need name, email and phone, and sign in with the email; staff need a username
 * (3-30 of a-z 0-9 . _ -); a ARTIST login also needs the artist it belongs to.
 */
public record AdminUserRequest(
        @NotNull Role role,
        @Schema(description = "Staff only: the sign-in name") @Size(max = 30) String username,
        @Schema(description = "New logins only") @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        @Schema(description = "PUT: false blocks the login") Boolean active,
        @Size(max = 60) String name,
        @Email @Size(max = 120) String email,
        @Size(max = 30) String phone,
        @Schema(description = "ARTIST only") String artistId) {
}
