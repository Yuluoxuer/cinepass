package com.cinepass.config;

import com.cinepass.serializer.FieldPermissionSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 字段权限初始化占位 — 接入业务 Mapper 后在此注入依赖。
 */
@Slf4j
@Configuration
public class FieldPermissionBootstrap {

    @PostConstruct
    public void init() {
        FieldPermissionSerializer.init();
        log.info("Field permission serializer stub initialized");
    }
}
