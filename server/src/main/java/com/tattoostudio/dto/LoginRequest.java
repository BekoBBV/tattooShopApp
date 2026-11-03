package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 120) @Schema(description = "Staff username, or a customer's email address") String username,
        @NotBlank @Size(max = 200) String password) {
}
