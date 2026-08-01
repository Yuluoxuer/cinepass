package com.minihr.security; // 权限包

import org.springframework.security.access.prepost.PreAuthorize; // 方法级鉴权

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任意已登录用户。等价 @PreAuthorize("isAuthenticated()")。
 * 调用方：Controller 如锁座/下单。覆盖已有 LoginRequired.java 加注释。无数据文件。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // 方法或类
@Retention(RetentionPolicy.RUNTIME)             // 运行时可见
@Documented                                     // 文档可见
@PreAuthorize("isAuthenticated()")              // 只要已认证
public @interface LoginRequired {
    // 标记注解，无属性
}
