package com.cinepass.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公开接口：未登录也可访问。等价于 {@code @PreAuthorize("permitAll()")}。
 * <p>
 * 注意：方法级与 {@link SecurityConfig} 路径级规则叠加，公开路径仍须在
 * {@code SecurityConfig} 的 {@code permitAll()} 中登记，否则过滤器链会先拒绝。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("permitAll()")
public @interface Public {
}
