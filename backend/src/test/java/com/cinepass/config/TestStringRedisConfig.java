package com.cinepass.config;

import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * test profile：用内存 Map 模拟 StringRedisTemplate（会话 / deny jti）。
 */
@Configuration
@Profile("test")
public class TestStringRedisConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        final Map<String, String> store = new ConcurrentHashMap<String, String>();
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);

        when(template.opsForValue()).thenReturn(ops);

        Answer<Object> setAnswer = invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            store.put(key, value);
            return null;
        };
        doAnswer(setAnswer).when(ops).set(anyString(), anyString());
        doAnswer(setAnswer).when(ops).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        when(ops.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));

        when(template.hasKey(anyString())).thenAnswer(invocation ->
                Boolean.valueOf(store.containsKey(invocation.getArgument(0))));
        when(template.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Boolean.valueOf(store.remove(key) != null);
        });

        return template;
    }
}
