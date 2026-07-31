package com.minihr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 应用启动测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(MockInfrastructureConfig.class)
class MinniHrApplicationTests {

    @Test
    void contextLoads() {
    }
}
