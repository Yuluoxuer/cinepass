package com.cinepass.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 固定窗口限流注解（Redis 实现，多实例共享计数）。
 *
 * <p>使用方式：
 * <pre>{@code
 * @RateLimit(key = "login", permits = 5, windowSeconds = 60)
 * @PostMapping("/login")
 * public Result<LoginVO> login(...)
 * }</pre>
 *
 * <p>限流 key 格式为 {@code rate:limit:<key>:<remoteAddr>}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流标识（会拼入 Redis key），如 "login"、"pay" */
    String key();

    /** 时间窗口内允许的最大请求次数 */
    int permits() default 10;

    /** 时间窗口秒数 */
    int windowSeconds() default 60;

    /** 限流提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
