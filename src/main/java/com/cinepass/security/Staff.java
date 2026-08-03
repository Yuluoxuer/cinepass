package com.cinepass.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工作人员或管理员可访问。等价于 {@code @PreAuthorize("hasAnyRole('staff','admin')")}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('staff','admin')")
public @interface Staff {
}
