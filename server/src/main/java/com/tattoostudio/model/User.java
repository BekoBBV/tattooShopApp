package com.tattoostudio.model;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A login: the owner (ADMIN), an artist (ARTIST) or a customer with an account (USER).
 * Staff sign in with a short username; customers use their email address as the username.
 * Customers can still book without an account (code + phone).
 */
@Document("users")
public class User {

    @Id
    private String id;
    @Indexed(unique = true)
    private String username;
    private String passwordHash;
    private Role role;
    /** The artist this login belongs to; null for the admin. Each artist has at most one login. */
    @Indexed(unique = true, sparse = true)
    private String artistId;
    private boolean active = true;
    /** Tokens issued before this moment are no longer accepted (set when the password changes). */
    private Instant passwordChangedAt;

    // Customer accounts only (null for staff)
    private String name;
    private String email;
    /** As the customer typed it, e.g. "0888 123 456"; bookings store it normalised. */
    private String phone;

    /** Customers: to fill in the booking form (bookings need 18+). */
    private LocalDate dateOfBirth;
    private Instant createdAt;
    private Instant lastLoginAt;
    /** How many times this login has signed in (shown on the admin's users page). */
    private int loginCount;

    public User() {
    }

    public User(String username, String passwordHash, Role role, String artistId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.artistId = artistId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    public void setPasswordChangedAt(Instant passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public int getLoginCount() { return loginCount; }
    public void setLoginCount(int loginCount) { this.loginCount = loginCount; }
}
