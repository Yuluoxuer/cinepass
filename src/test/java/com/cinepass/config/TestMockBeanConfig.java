package com.cinepass.config;

import org.mockito.Mockito;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 测试环境 Mock Bean 配置（仅 test profile 生效）
 */
@Configuration
@Profile("test")
public class TestMockBeanConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    /** 推荐缓存用的 RedissonClient mock：getBucket 返回空桶、getLock 返回抢不到锁，走 DB 降级 */
    @Bean
    @Primary
    public RedissonClient redissonClient() {
        RedissonClient client = Mockito.mock(RedissonClient.class);
        when(client.getBucket(anyString(), any())).thenReturn(Mockito.mock(RBucket.class));
        when(client.getLock(anyString())).thenReturn(Mockito.mock(RLock.class));
        return client;
    }
}
