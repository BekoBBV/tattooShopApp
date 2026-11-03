package com.tattoostudio.controller;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.AdminUserDto;
import com.tattoostudio.dto.AdminUserRequest;
import com.tattoostudio.dto.ListQuery;
import com.tattoostudio.dto.PageDto;
import com.tattoostudio.dto.PasswordRequest;
import com.tattoostudio.model.Role;
import com.tattoostudio.service.AdminUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Admin: every login and its rights (guest → customer → artist → admin). */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin users", description = "Logins, roles and blocking (admin only)")
@SecurityRequirement(name = "bearer")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "All logins", description = "role, active (true / false), search by username, name, email or phone; "
            + "sort by username, name, role, created or lastLogin.")
    public PageDto<AdminUserDto> list(@Valid @ParameterObject ListQuery list,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active) {
        return users.search(list.text(), role, active, list);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a login", description = "Customers: name, email (their sign-in), phone. Staff: username; "
            + "artists also artistId. Always a password of 8+ characters.")
    public AdminUserDto create(@Valid @RequestBody AdminUserRequest request) {
        return users.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Change a login's rights", description = "Role, active (false = blocked), artistId for artists, "
            + "name and phone for customers. Not your own login; the last active admin stays admin.")
    public AdminUserDto update(@PathVariable String id, @Valid @RequestBody AdminUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return users.update(id, request, jwt.getSubject());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password", description = "Signs that person out everywhere.")
    public void setPassword(@PathVariable String id, @Valid @RequestBody PasswordRequest request) {
        users.setPassword(id, request.password());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a login", description = "A customer's bookings stay, without the account.")
    public void delete(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        users.delete(id, jwt.getSubject());
    }
}
