package com.minihr.aop;

import java.lang.annotation.*;

/**
 * 操作审计注解 — 标记在 Controller 方法上，由 AuditLogAspect 记录日志。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    String module();

    String action();

    String targetType() default "";

    String targetIdExpr() default "";
}
