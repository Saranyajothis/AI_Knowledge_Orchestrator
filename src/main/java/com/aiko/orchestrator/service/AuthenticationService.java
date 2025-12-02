package com.aiko.orchestrator.service;

import com.aiko.orchestrator.dto.AuthenticationResponse;
import com.aiko.orchestrator.dto.LoginRequest;
import com.aiko.orchestrator.dto.RegisterRequest;
import com.aiko.orchestrator.exception.ResourceAlreadyExistsException;
import com.aiko.orchestrator.exception.UnauthorizedException;
import com.aiko.orchestrator.model.User;
import com.aiko.orchestrator.repository.UserRepository;
import com.aiko.orchestrator.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    
    public AuthenticationResponse register(RegisterRequest request) {
        log.debug("Registering new user: {}", request.getUsername());
        
        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already exists: " + request.getUsername());
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already exists: " + request.getEmail());
        }
        
        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of("USER"))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getUsername());
        
        // Generate tokens
        String accessToken = jwtService.generateToken(savedUser);
        String refreshToken = jwtService.generateRefreshToken(savedUser);
        
        // Save refresh token to user
        savedUser.setRefreshToken(refreshToken);
        savedUser.setRefreshTokenExpiryDate(
            LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirationTime() / 1000)
        );
        userRepository.save(savedUser);
        
        return buildAuthenticationResponse(savedUser, accessToken, refreshToken);
    }
    
    public AuthenticationResponse login(LoginRequest request) {
        log.debug("Authenticating user: {}", request.getUsernameOrEmail());
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getUsernameOrEmail(),
                    request.getPassword()
                )
            );
            
            User user = (User) authentication.getPrincipal();
            
            // Update last login time
            user.setLastLoginAt(LocalDateTime.now());
            
            // Generate tokens
            String accessToken = jwtService.generateToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);
            
            // Save refresh token
            user.setRefreshToken(refreshToken);
            user.setRefreshTokenExpiryDate(
                LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirationTime() / 1000)
            );
            userRepository.save(user);
            
            log.info("User authenticated successfully: {}", user.getUsername());
            return buildAuthenticationResponse(user, accessToken, refreshToken);
            
        } catch (BadCredentialsException e) {
            log.error("Authentication failed for user: {}", request.getUsernameOrEmail());
            throw new UnauthorizedException("Invalid username or password");
        }
    }
    
    public AuthenticationResponse refreshToken(String refreshToken) {
        log.debug("Refreshing token");
        
        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        
        // Check if refresh token is expired
        if (user.getRefreshTokenExpiryDate().isBefore(LocalDateTime.now())) {
            user.setRefreshToken(null);
            user.setRefreshTokenExpiryDate(null);
            userRepository.save(user);
            throw new UnauthorizedException("Refresh token expired");
        }
        
        // Generate new access token
        String newAccessToken = jwtService.generateToken(user);
        
        log.info("Token refreshed successfully for user: {}", user.getUsername());
        return buildAuthenticationResponse(user, newAccessToken, refreshToken);
    }
    
    public void logout(String accessToken, String refreshToken) {
        try {
            String username = jwtService.extractUsername(accessToken);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));
            
            // Clear refresh token
            user.setRefreshToken(null);
            user.setRefreshTokenExpiryDate(null);
            userRepository.save(user);
            
            log.info("User logged out successfully: {}", username);
        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage());
            throw new UnauthorizedException("Invalid token");
        }
    }
    
    public boolean validateToken(String token) {
        return jwtService.validateToken(token);
    }
    
    private AuthenticationResponse buildAuthenticationResponse(User user, String accessToken, String refreshToken) {
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime() / 1000) // Convert to seconds
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .build();
    }
}
