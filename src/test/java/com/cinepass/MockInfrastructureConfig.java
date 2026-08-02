package com.cinepass;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * test profile 下为 RedisUtil 提供 mock 底层依赖。
 */
@TestConfiguration
public class MockInfrastructureConfig {

    @Bean
    @Primary
    RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}
