package com.cinepass.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        String token = jwtUtil.generateAccessToken(userId, "alice", Roles.STAFF, "sid-001", "c12");
        assertTrue(jwtUtil.validateToken(token));

        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(userId, info.getUserId());
        assertEquals("alice", info.getUsername());
        assertEquals(Roles.STAFF, info.getRole());
        assertEquals("c12", info.getCinemaId());
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
    void generate_nullRole_shouldDefaultToUser() {
        String token = jwtUtil.generateAccessToken("u018f3c4e9a7b70c0a1b2c3d4e5f60718", "bob",
                null, "sid-null");
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(Roles.USER, info.getRole());
        assertNull(info.getCinemaId());
    }

    @Test
    void generate_blankCinemaId_shouldOmitClaim() {
        String token = jwtUtil.generateAccessToken("u018f3c4e9a7b70c0a1b2c3d4e5f60718", "bob",
                Roles.USER, "sid-1", "");
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertNull(info.getCinemaId());
    }

    @Test
    void validate_tamperedOrGarbage_shouldFail() {
        assertFalse(jwtUtil.validateToken(null));
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
        String token = jwtUtil.generateAccessToken("u1", "a", Roles.USER, "s1");
        assertFalse(jwtUtil.validateToken(token + "x"));
        assertFalse(jwtUtil.isSignatureValid(token + "x"));
        assertFalse(jwtUtil.isExpired("garbage"));
        assertNull(jwtUtil.parseAccessToken("garbage"));
        assertNull(jwtUtil.parseUserId("garbage"));
    }

    @Test
    void shortSecret_shouldStillSignAndValidate() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "short");
        String token = jwtUtil.generateAccessToken("u018f3c4e9a7b70c0a1b2c3d4e5f60718", "a",
                Roles.ADMIN, "sid");
        assertTrue(jwtUtil.validateToken(token));
        assertEquals(Roles.ADMIN, jwtUtil.parseAccessToken(token).getRole());
    }

    @Test
    void parseAllowExpired_shouldWorkWhenExpired() throws Exception {
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpireSeconds", 1L);
        String token = jwtUtil.generateAccessToken("u018f3c4e9a7b70c0a1b2c3d4e5f60718", "alice",
                Roles.USER, "sid-exp");
        Thread.sleep(1200L);
        assertTrue(jwtUtil.isExpired(token));
        assertFalse(jwtUtil.validateToken(token));
        assertTrue(jwtUtil.isSignatureValid(token));
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessTokenAllowExpired(token);
        assertNotNull(info);
        assertEquals("sid-exp", info.getSid());
        assertEquals(1L, jwtUtil.getExpiresInSeconds());
    }
}
