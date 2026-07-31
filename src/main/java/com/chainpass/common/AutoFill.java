package com.minihr.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计字段自动填充注解
 * <p>
 * 标在 Entity 字段上，由 {@code AutoFillInterceptor} 在 SQL 执行前自动设值。
 * 字段值为 null 时才填充，业务代码显式设的值不会被覆盖。
 *
 * @author hx
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {

    /** 触发时机 */
    FillType value() default FillType.INSERT;

    /** 填充时机枚举 */
    enum FillType {
        /** 仅在 INSERT 时填充（如 createdAt） */
        INSERT,
        /** 仅在 UPDATE 时填充（如 updatedAt） */
        UPDATE,
        /** INSERT 和 UPDATE 都填充（如 updatedAt，创建时也设初值） */
        BOTH
    }

}
