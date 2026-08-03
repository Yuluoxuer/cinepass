package com.cinepass.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任意已登录用户可访问。等价于 {@code @PreAuthorize("isAuthenticated()")}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("isAuthenticated()")
public @interface LoginUser {
}
