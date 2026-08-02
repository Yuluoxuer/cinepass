package com.cinepass.aop;

import com.cinepass.security.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 操作审计切面占位 — 接入 SysOperationLogMapper 后在此持久化审计记录。
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.debug("[AuditLog] userId={} module={} action={} durationMs={} status=success",
                    SecurityContext.getCurrentUserId(),
                    auditLog.module(),
                    auditLog.action(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.debug("[AuditLog] userId={} module={} action={} durationMs={} status=failure error={}",
                    SecurityContext.getCurrentUserId(),
                    auditLog.module(),
                    auditLog.action(),
                    System.currentTimeMillis() - start,
                    e.getMessage());
            throw e;
        }
    }
}
