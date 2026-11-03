package com.tattoostudio.dto;

import com.tattoostudio.model.Role;

/** Who is signed in; artistId is set for artists only, name for customers only. */
public record AuthUserDto(String username, Role role, String artistId, String name) {
}
