package com.tattoostudio.dto;

import jakarta.validation.constraints.NotNull;

/** Shows / hides a service or (de)activates an artist, without touching anything else. */
public record ActiveRequest(@NotNull Boolean active) {
}
