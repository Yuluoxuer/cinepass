package com.cinepass.common;

/**
 * 限流异常，由 {@link com.cinepass.aop.RateLimitAspect} 抛出。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
