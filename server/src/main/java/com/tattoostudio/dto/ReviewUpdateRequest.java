package com.tattoostudio.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewUpdateRequest(@NotNull Boolean approved) {
}
