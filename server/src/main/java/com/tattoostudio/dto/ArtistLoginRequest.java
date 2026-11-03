package com.tattoostudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates or resets an artist's login. */
public record ArtistLoginRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,30}", message = "3-30 letters, digits, dots, dashes or underscores") String username,
        @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters") String password) {
}
