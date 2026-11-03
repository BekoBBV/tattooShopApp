package com.tattoostudio.dto;

import java.util.List;

import com.tattoostudio.model.DayException;
import com.tattoostudio.model.WorkingHours;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An artist's regular weekly hours plus one-off date exceptions (day off or custom hours). */
public record ScheduleDto(
        @NotNull @Size(max = 7) List<WorkingHours> weeklyHours,
        @NotNull @Size(max = 100) List<DayException> exceptions) {
}
