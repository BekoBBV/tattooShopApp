package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A message from the contact form. */
public record CreateMessageRequest(
        @NotBlank @Size(max = 60) String name,
        @NotBlank @Size(max = 120) @Schema(description = "Email or phone number", example = "0888 123 456") String contact,
        @NotBlank @Size(max = 1000) String text) {
}
