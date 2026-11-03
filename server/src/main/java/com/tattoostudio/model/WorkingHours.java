package com.tattoostudio.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/** An artist's regular hours for one day of the week, e.g. TUESDAY 10:00–19:00. */
public record WorkingHours(
        DayOfWeek day,
        @JsonFormat(pattern = "HH:mm") LocalTime start,
        @JsonFormat(pattern = "HH:mm") LocalTime end) {
}
