package com.chronos.service;

import com.chronos.domain.User;
import com.chronos.dto.AuthResponse;
import com.chronos.dto.LoginRequest;
import com.chronos.dto.RegisterRequest;
import com.chronos.exception.BadRequestException;
import com.chronos.exception.ConflictException;
import com.chronos.repository.UserRepository;
import com.chronos.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    @InjectMocks private AuthService authService;

    @Test
    void register_success_returns_token_and_username() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtUtil.generate("alice")).thenReturn("jwt-token");

        AuthResponse response = authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throws_conflict_when_username_taken() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Username already taken");
    }

    @Test
    void register_throws_conflict_when_email_taken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_success_returns_token_and_username() {
        User user = new User("alice", "alice@example.com", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generate("alice")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void login_throws_bad_request_when_user_not_found() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "pass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_throws_bad_request_when_wrong_password() {
        User user = new User("alice", "alice@example.com", "hashed");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid credentials");
    }
}
