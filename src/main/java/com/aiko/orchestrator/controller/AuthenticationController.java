package com.aiko.orchestrator.controller;

import com.aiko.orchestrator.dto.AuthenticationResponse;
import com.aiko.orchestrator.dto.LoginRequest;
import com.aiko.orchestrator.dto.RegisterRequest;
import com.aiko.orchestrator.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication management APIs")
public class AuthenticationController {
    
    private final AuthenticationService authenticationService;
    
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "User already exists")
    })
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("Processing registration request for username: {}", request.getUsername());
        AuthenticationResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/login")
    @Operation(summary = "Authenticate user and get tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("Processing login request for user: {}", request.getUsernameOrEmail());
        AuthenticationResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    })
    public ResponseEntity<AuthenticationResponse> refreshToken(
            @RequestHeader("Refresh-Token") String refreshToken) {
        log.info("Processing token refresh request");
        AuthenticationResponse response = authenticationService.refreshToken(refreshToken);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "Logout user and invalidate tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "Refresh-Token", required = false) String refreshToken) {
        log.info("Processing logout request");
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        authenticationService.logout(token, refreshToken);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token is valid"),
        @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    public ResponseEntity<Boolean> validateToken(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        boolean isValid = authenticationService.validateToken(token);
        return ResponseEntity.ok(isValid);
    }
}
