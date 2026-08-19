package com.cinepass.config;

import com.cinepass.common.AutoFill;
import com.cinepass.common.AutoFill.FillType;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * MyBatis 审计字段自动填充拦截器
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class AutoFillInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        SqlCommandType sqlType = ms.getSqlCommandType();
        Object parameter = invocation.getArgs()[1];

        if (parameter != null && sqlType != null) {
            if (parameter instanceof Map) {
                for (Object value : ((Map<?, ?>) parameter).values()) {
                    autoFillEntity(value, sqlType);
                }
            } else {
                autoFillEntity(parameter, sqlType);
            }
        }

        return invocation.proceed();
    }

    private void autoFillEntity(Object entity, SqlCommandType sqlType) {
        if (entity == null || entity instanceof Collection) {
            return;
        }

        MetaObject meta = SystemMetaObject.forObject(entity);

        for (Field field : entity.getClass().getDeclaredFields()) {
            AutoFill annotation = field.getAnnotation(AutoFill.class);
            if (annotation == null || !match(annotation.value(), sqlType)) {
                continue;
            }

            String fieldName = field.getName();
            if (!meta.hasSetter(fieldName)) {
                continue;
            }

            Object current = meta.getValue(fieldName);
            if (current != null) {
                continue;
            }

            meta.setValue(fieldName, LocalDateTime.now());
        }
    }

    private boolean match(FillType fillType, SqlCommandType sqlType) {
        if (fillType == FillType.BOTH) {
            return true;
        }
        if (fillType == FillType.INSERT && SqlCommandType.INSERT.equals(sqlType)) {
            return true;
        }
        return fillType == FillType.UPDATE && SqlCommandType.UPDATE.equals(sqlType);
    }
}
