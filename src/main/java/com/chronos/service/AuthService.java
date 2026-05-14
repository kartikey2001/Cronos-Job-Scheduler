package com.chronos.service;

import com.chronos.domain.User;
import com.chronos.dto.AuthResponse;
import com.chronos.dto.LoginRequest;
import com.chronos.dto.RegisterRequest;
import com.chronos.exception.BadRequestException;
import com.chronos.exception.ConflictException;
import com.chronos.repository.UserRepository;
import com.chronos.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }
        User user = new User(request.username(), request.email(),
                passwordEncoder.encode(request.password()));
        userRepository.save(user);
        return new AuthResponse(jwtUtil.generate(user.getUsername()), user.getUsername());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }
        return new AuthResponse(jwtUtil.generate(user.getUsername()), user.getUsername());
    }
}
