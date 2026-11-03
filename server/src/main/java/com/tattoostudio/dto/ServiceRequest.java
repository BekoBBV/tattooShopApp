package com.tattoostudio.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A service as the admin creates or edits it. {@code active} is optional (null keeps the current value). */
public record ServiceRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 300) String description,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2) BigDecimal price,
        @Size(max = 12) String label,
        @NotBlank @Pattern(regexp = "[a-z]{2,20}", message = "must be a palette name like blue or sunset") String palette,
        Boolean active) {
}
