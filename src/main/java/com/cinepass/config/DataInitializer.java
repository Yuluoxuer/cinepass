package com.minihr.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动数据初始化占位 — 业务种子数据请在专用初始化器中实现。
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.debug("[DataInitializer] skip — no global seed data");
    }
}
