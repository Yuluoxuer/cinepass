package com.cinepass.security; // 权限包

import org.springframework.security.access.prepost.PreAuthorize; // 方法级鉴权

import java.lang.annotation.Documented;      // 进入 JavaDoc
import java.lang.annotation.ElementType;     // 可标注位置
import java.lang.annotation.Retention;       // 保留策略
import java.lang.annotation.RetentionPolicy; // RUNTIME 才能被 Security 读取
import java.lang.annotation.Target;          // 限制标注位置

/**
 * 仅 admin。等价 @PreAuthorize("hasRole('admin')")。
 * 调用方：Controller 如 /admin/users。覆盖已有 Admin.java 加注释。无数据文件。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // 方法或类
@Retention(RetentionPolicy.RUNTIME)             // 运行时可见
@Documented                                     // 文档可见
@PreAuthorize("hasRole('admin')")               // 真正鉴权：只要 admin
public @interface Admin {
    // 标记注解，无属性
}
