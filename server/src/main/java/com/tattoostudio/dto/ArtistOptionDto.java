package com.tattoostudio.dto;

/** An artist for the admin's pickers (filters, "which artist is this login"): no hours or bookings. */
public record ArtistOptionDto(String id, String name, boolean active, String username) {
}
