package com.chronos.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2";
    private static final long EXPIRY_MS = 3_600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRY_MS);
    }

    @Test
    void generate_and_extractUsername_roundTrip() {
        String token = jwtUtil.generate("alice");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void isValid_returns_true_for_fresh_token() {
        assertThat(jwtUtil.isValid(jwtUtil.generate("bob"))).isTrue();
    }

    @Test
    void isValid_returns_false_for_tampered_token() {
        String token = jwtUtil.generate("carol");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_returns_false_for_expired_token() {
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1000L);
        String token = expiredUtil.generate("dave");
        assertThat(jwtUtil.isValid(token)).isFalse();
    }

    @Test
    void isValid_returns_false_for_empty_string() {
        assertThat(jwtUtil.isValid("")).isFalse();
    }
}
