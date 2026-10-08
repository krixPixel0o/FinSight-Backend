package com.finsight.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JwtUtilTest {
    @Autowired private JwtUtil jwtUtil;

    @Test
    void validatesSignedTokenAndRejectsTampering() {
        String token = jwtUtil.generateToken("token-check@example.com");
        assertTrue(jwtUtil.validateToken(token));
        assertFalse(jwtUtil.validateToken(token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a")));
    }
}
