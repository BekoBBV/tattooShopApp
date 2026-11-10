package com.tattoostudio.dto;

import java.math.BigDecimal;
import java.util.List;

import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An artist as the admin creates or edits them. Hours and exceptions are checked like an artist's
 * own schedule. {@code slug} (the page address) is made from the name when a new artist has none, and
 * stays as it is when left empty on an edit;
 * {@code breakMinutes} and {@code active} are optional (null keeps the current value).
 */
public record ArtistRequest(
        @NotBlank @Size(max = 60) @Schema(example = "Kai") String name,
        @Size(max = 40) @Pattern(regexp = "[a-z0-9-]*", message = "use lower-case letters, digits and dashes")
        @Schema(example = "kai", nullable = true) String slug,
        @Size(max = 60) @Schema(example = "Bold traditional colour") String style,
        @Size(max = 600) String bio,
        @DecimalMin("0") @DecimalMax("1000") @Schema(example = "90", nullable = true) BigDecimal hourlyRate,
        @DecimalMin("0") @DecimalMax("5000") @Schema(example = "550", nullable = true) BigDecimal dayRate,
        @Schema(example = "30", nullable = true) Integer breakMinutes,
        @Size(max = 7) List<WorkingHours> weeklyHours,
        @Size(max = 100) List<DayException> exceptions,
        Boolean active) {
}
