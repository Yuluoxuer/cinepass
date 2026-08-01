package com.minihr.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调用方：{@code mvn test -Dtest=JwtUtilTest}（Surefire）。
 * Glob：无已有 JwtUtilTest。不读写数据文件；合成 userId=42 / role=staff。
 * 用户指令：「现在按照系分文档要求帮我完成权限部分，并告诉我怎么在接口上赋予权限」
 */
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
        String token = jwtUtil.generateAccessToken(42L, "alice", Roles.STAFF, "sid-001");
        assertTrue(jwtUtil.validateToken(token));

        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(42L, info.getUserId());
        assertEquals("alice", info.getUsername());
        assertEquals(Roles.STAFF, info.getRole());
        assertEquals("sid-001", info.getSid());
        assertNotNull(info.getJti());
        assertEquals(1, info.getRoles().size());
        assertEquals(Roles.STAFF, info.getRoles().get(0));
    }

    @Test
    void generateWithRolesList_shouldUseFirstAsRole() {
        String token = jwtUtil.generateAccessToken(1L, "admin",
                Collections.singletonList(Roles.ADMIN));
        JwtUtil.JwtUserInfo info = jwtUtil.parseAccessToken(token);
        assertNotNull(info);
        assertEquals(Roles.ADMIN, info.getRole());
    }
}
