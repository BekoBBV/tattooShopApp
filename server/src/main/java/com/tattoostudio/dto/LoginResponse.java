package com.tattoostudio.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, AuthUserDto user) {
}
