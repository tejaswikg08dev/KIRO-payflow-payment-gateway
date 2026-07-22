package com.payflow.identity.service;

import com.payflow.common.exception.DuplicateResourceException;
import com.payflow.common.exception.PayflowException;
import com.payflow.common.util.IdGenerator;
import com.payflow.identity.dto.AuthResponse;
import com.payflow.identity.dto.LoginRequest;
import com.payflow.identity.dto.RegisterRequest;
import com.payflow.identity.model.User;
import com.payflow.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Register a new user.
     * 1. Check if email already exists
     * 2. Hash password with BCrypt
     * 3. Save user to database
     * 4. Generate JWT tokens
     * 5. Return auth response
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("DUPLICATE_EMAIL",
                    "A user with email '" + request.getEmail() + "' already exists");
        }

        // Parse role
        User.Role role;
        try {
            role = User.Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PayflowException("INVALID_ROLE",
                    "Role must be CUSTOMER or MERCHANT", HttpStatus.BAD_REQUEST);
        }

        // Create user entity
        User user = User.builder()
                .id(IdGenerator.userId())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(role)
                .emailVerified(false)
                .status(User.UserStatus.ACTIVE)
                .build();

        // Save to database
        userRepository.save(user);
        log.info("User registered: {} ({})", user.getId(), user.getEmail());

        // Generate tokens and return
        return buildAuthResponse(user);
    }

    /**
     * Login with email and password.
     * 1. Find user by email
     * 2. Verify password with BCrypt
     * 3. Generate JWT tokens
     * 4. Update last login timestamp
     * 5. Return auth response
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Find user
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new PayflowException("INVALID_CREDENTIALS",
                        "Email or password is incorrect", HttpStatus.UNAUTHORIZED));

        // Check account status
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new PayflowException("ACCOUNT_SUSPENDED",
                    "Your account has been suspended", HttpStatus.FORBIDDEN);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new PayflowException("INVALID_CREDENTIALS",
                    "Email or password is incorrect", HttpStatus.UNAUTHORIZED);
        }

        // Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: {} ({})", user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(
                user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 minutes in seconds
                .user(AuthResponse.UserInfo.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .build())
                .build();
    }
}
