package com.tattoostudio.dto;

import java.time.Instant;

/** A customer's own account details (for "Signed in as …" and filling in the booking form). */
public record AccountDto(String name, String email, String phone, Instant createdAt) {
}
