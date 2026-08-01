package com.minihr.security; // 权限包

import org.springframework.security.access.prepost.PreAuthorize; // 方法级鉴权

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * staff 或 admin。等价 @PreAuthorize("hasAnyRole('staff','admin')")。
 * 调用方：Controller 如 /admin/movies、seat-maps。覆盖已有 Staff.java 加注释。无数据文件。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // 方法或类
@Retention(RetentionPolicy.RUNTIME)             // 运行时可见
@Documented                                     // 文档可见
@PreAuthorize("hasAnyRole('staff','admin')")    // staff 或 admin
public @interface Staff {
    // 标记注解，无属性
}
