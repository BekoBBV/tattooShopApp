package com.tattoostudio.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tattoostudio.dto.AuthUserDto;
import com.tattoostudio.dto.LoginRequest;
import com.tattoostudio.dto.LoginResponse;
import com.tattoostudio.dto.RegisterRequest;
import com.tattoostudio.security.StaffUser;
import com.tattoostudio.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Login", description = "Sign in (staff and customers), create a customer account")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Returns a token valid for 12 hours. Send it as 'Authorization: Bearer <token>'.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return auth.login(request.username(), request.password(), http.getRemoteAddr());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a customer account",
            description = "Signs the new customer in straight away (same answer as sign in). Sign in later with the email.")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return auth.register(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    @Operation(summary = "Who is signed in")
    public AuthUserDto me(@AuthenticationPrincipal Jwt jwt) {
        StaffUser user = StaffUser.from(jwt);
        return new AuthUserDto(user.username(), user.role(), user.artistId(), jwt.getClaimAsString("name"));
    }
}
