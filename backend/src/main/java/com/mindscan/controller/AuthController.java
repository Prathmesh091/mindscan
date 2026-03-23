package com.mindscan.controller;

import com.mindscan.dto.Dtos;
import com.mindscan.model.User;
import com.mindscan.repository.UserRepository;
import com.mindscan.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil             jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<Dtos.AuthResponse> register(
            @Valid @RequestBody Dtos.RegisterRequest req) {

        // Check username uniqueness
        if (userRepository.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest().body(
                Dtos.AuthResponse.builder()
                    .success(false)
                    .message("Username already taken. Please choose another.")
                    .build()
            );
        }

        // Generate email if not provided (Android app sends username@mindscan.app)
        String email = (req.getEmail() != null && !req.getEmail().isBlank())
                ? req.getEmail()
                : req.getUsername() + "@mindscan.app";

        // Check email uniqueness
        if (userRepository.existsByEmail(email)) {
            // Auto-adjust email to avoid conflict
            email = req.getUsername() + "_" + System.currentTimeMillis() + "@mindscan.app";
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", req.getUsername());

        String token = jwtUtil.generateToken(user.getUsername());

        return ResponseEntity.ok(
            Dtos.AuthResponse.builder()
                .success(true)
                .message("Registration successful! Welcome to MindScan.")
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .build()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<Dtos.AuthResponse> login(
            @Valid @RequestBody Dtos.LoginRequest req) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    req.getUsername(), req.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(
                Dtos.AuthResponse.builder()
                    .success(false)
                    .message("Invalid username or password")
                    .build()
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body(
                Dtos.AuthResponse.builder()
                    .success(false)
                    .message("Authentication failed")
                    .build()
            );
        }

        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtil.generateToken(user.getUsername());
        log.info("User logged in: {}", req.getUsername());

        return ResponseEntity.ok(
            Dtos.AuthResponse.builder()
                .success(true)
                .message("Login successful")
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .build()
        );
    }
}
