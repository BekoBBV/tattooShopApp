package com.tattoostudio.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.tattoostudio.model.Role;

/** The signed-in staff member, read from their login token. */
public record StaffUser(String username, Role role, String artistId) {

    public static StaffUser from(Jwt jwt) {
        return new StaffUser(jwt.getSubject(), Role.valueOf(jwt.getClaimAsString("role")), jwt.getClaimAsString("artistId"));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
