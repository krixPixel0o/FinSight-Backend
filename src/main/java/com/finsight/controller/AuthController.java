package com.finsight.controller;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.finsight.dto.LoginRequest;
import com.finsight.dto.LoginResponse;
import com.finsight.dto.RegisterRequest;
import com.finsight.dto.UserResponse;
import com.finsight.dto.ChangePasswordRequest;
import com.finsight.dto.UserProfileRequest;
import com.finsight.entity.User;
import com.finsight.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/API/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = authService.registerUser(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("User registered successfully with ID: " + user.getId());
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        String token = authService.loginUser(request);

        return ResponseEntity.ok(
                new LoginResponse(token)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Principal principal) {
        return ResponseEntity.ok(
                UserResponse.from(authService.getUserByEmail(principal.getName()))
        );
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(Principal principal,
                                                     @Valid @RequestBody UserProfileRequest request) {
        User user = authService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(Principal principal,
                                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getName(), request);
        return ResponseEntity.noContent().build();
    }
}