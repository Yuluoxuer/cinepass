package com.cinepass.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-jwt-secret-key-32bytes-min!!");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireSeconds", 3600L);
    }

    @Test
    void generateAndParse_shouldCarryRoleSidJti() {
        String userId = "u018f3c4e9a7b70c0a1b2c3d4e5f60718";
        String token = jwtUtil.generateAccessToken(userId, "alice", Roles.STAFF, "c018f3c4e9a7b70c0a1b2c3d4e5f60718", "sid-001");
        assertTrue(jwtUtil.validateToken(token));

        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(userId, info.getUserId());
        assertEquals("alice", info.getUsername());
        assertEquals(Roles.STAFF, info.getRole());
        assertEquals("c018f3c4e9a7b70c0a1b2c3d4e5f60718", info.getCinemaId());
        assertEquals("sid-001", info.getSid());
        assertNotNull(info.getJti());
        assertEquals(1, info.getRoles().size());
        assertEquals(Roles.STAFF, info.getRoles().get(0));
    }

    @Test
    void generateWithRolesList_shouldUseFirstAsRole() {
        String token = jwtUtil.generateAccessToken("u00000000000000000000000000000003", "admin",
                Collections.singletonList(Roles.ADMIN));
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(Roles.ADMIN, info.getRole());
    }

    @Test
    void parseAllowExpired_shouldWorkWhenExpired() throws Exception {
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireSeconds", 1L);
        String token = jwtUtil.generateAccessToken("u018f3c4e9a7b70c0a1b2c3d4e5f60718", "alice",
                Roles.USER, "sid-exp");
        Thread.sleep(1200L);
        assertTrue(jwtUtil.isExpired(token));
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessTokenAllowExpired(token);
        assertNotNull(info);
        assertEquals("sid-exp", info.getSid());
    }
}
