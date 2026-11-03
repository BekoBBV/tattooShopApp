package com.tattoostudio.dto;

import jakarta.validation.constraints.NotNull;

public record MessageUpdateRequest(@NotNull Boolean read) {
}
