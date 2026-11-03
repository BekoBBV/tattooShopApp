package com.tattoostudio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a customer enters on the "Manage booking" page. */
public record BookingLookupRequest(
        @NotBlank @Size(max = 20) @Schema(example = "FS-7K3QXM") String code,
        @NotBlank @Size(max = 30) @Schema(example = "0888 123 456") String phone) {
}
