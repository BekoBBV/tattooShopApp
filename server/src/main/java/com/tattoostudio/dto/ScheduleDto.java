package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An artist's regular weekly hours, one-off date exceptions (day off or custom hours) and the break
 * after each client ({@code breakMinutes}, optional: null keeps the current one).
 */
public record ScheduleDto(
        @NotNull @Size(max = 7) List<WorkingHours> weeklyHours,
        @NotNull @Size(max = 100) List<DayException> exceptions,
        @Schema(description = "Minutes kept free after each client (0–120, steps of 30)", example = "30", nullable = true)
        Integer breakMinutes) {
}
