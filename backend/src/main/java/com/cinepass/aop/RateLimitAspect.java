package com.cinepass.aop;

import com.cinepass.common.RateLimitException;
import com.cinepass.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 固定窗口限流切面（Redis INCR + EXPIRE）。
 *
 * <p>命中 key 为 {@code rate:limit:<key>:<remoteAddr>}，
 * 首次请求设置 TTL，超过 permits 则抛出 {@link RateLimitException}。
 * Redis 不可用时降级放行（不阻塞业务）。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = "rate:limit:";

    private final RedisUtil redisUtil;
    private final HttpServletRequest request;

    public RateLimitAspect(RedisUtil redisUtil, HttpServletRequest request) {
        this.redisUtil = redisUtil;
        this.request = request;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String redisKey = KEY_PREFIX + rateLimit.key() + ":" + getClientIp();

        try {
            Long count = redisUtil.incr(redisKey);
            if (count != null && count == 1) {
                redisUtil.expire(redisKey, rateLimit.windowSeconds());
            }

            if (count != null && count > rateLimit.permits()) {
                log.warn("Rate limit hit: key={} ip={} count={}/{}/{}s",
                        rateLimit.key(), getClientIp(), count, rateLimit.permits(), rateLimit.windowSeconds());
                throw new RateLimitException(rateLimit.message());
            }
        } catch (RateLimitException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时降级放行
            log.error("Rate limit Redis error, fallback pass: {}", e.getMessage());
        }

        return joinPoint.proceed();
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
