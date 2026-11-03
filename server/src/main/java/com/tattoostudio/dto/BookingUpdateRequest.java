package com.tattoostudio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The admin correcting a booking's customer details and notes (time, artist and service stay). */
public record BookingUpdateRequest(
        @NotBlank @Size(max = 60) String customerName,
        @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 120) String email,
        @Size(max = 300) String notes) {
}
