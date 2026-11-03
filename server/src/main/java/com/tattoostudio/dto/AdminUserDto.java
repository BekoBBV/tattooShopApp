package com.tattoostudio.dto;

import java.time.Instant;

import com.tattoostudio.model.Role;
import com.tattoostudio.model.User;

/** One login as the admin sees it on the users & rights page (never the password hash). */
public record AdminUserDto(
        String id,
        String username,
        Role role,
        boolean active,
        String name,
        String email,
        String phone,
        String artistId,
        String artistName,
        Instant createdAt,
        Instant lastLoginAt,
        int loginCount) {

    public static AdminUserDto from(User u, String artistName) {
        return new AdminUserDto(u.getId(), u.getUsername(), u.getRole(), u.isActive(), u.getName(), u.getEmail(),
                u.getPhone(), u.getArtistId(), artistName, u.getCreatedAt(), u.getLastLoginAt(), u.getLoginCount());
    }
}
