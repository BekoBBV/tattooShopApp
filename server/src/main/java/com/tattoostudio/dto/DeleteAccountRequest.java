package com.tattoostudio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A customer deleting their own account, confirmed with their password. */
public record DeleteAccountRequest(@NotBlank @Size(max = 200) String password) {
}
