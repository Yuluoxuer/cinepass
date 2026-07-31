package com.minihr.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 封装 RedisTemplate&lt;String, Object&gt; 常用操作，统一过期时间单位为秒。
 * 无 Redis 环境（如 test profile）不注册本 Bean。
 */
@Component
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ============ 通用操作 ============

    /**
     * 设置 key 的过期时间（秒）
     *
     * @return true 设置成功
     * @throws IllegalArgumentException 如果 key 为空
     */
    public boolean expire(String key, long seconds) {
        requireNonEmptyKey(key);
        return Boolean.TRUE.equals(redisTemplate.expire(key, seconds, TimeUnit.SECONDS));
    }

    /**
     * 获取 key 的剩余过期时间（秒）
     * -1 表示永不过期；-2 表示 key 不存在
     */
    public long getExpire(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2L : ttl;
    }

    /**
     * 判断 key 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 删除一个或多个 key
     */
    public void delete(String... keys) {
        if (keys.length == 1) {
            redisTemplate.delete(keys[0]);
        } else {
            redisTemplate.delete(Arrays.asList(keys));
        }
    }

    // ============ String 操作 ============

    /**
     * 获取值
     *
     * @throws IllegalArgumentException 如果 key 为空
     */
    public Object get(String key) {
        requireNonEmptyKey(key);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 设置值（永不过期）
     *
     * @throws IllegalArgumentException 如果 key 为空
     */
    public void set(String key, Object value) {
        requireNonEmptyKey(key);
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置值并指定过期时间（秒）
     *
     * @throws IllegalArgumentException 如果 key 为空
     */
    public void set(String key, Object value, long seconds) {
        requireNonEmptyKey(key);
        redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * key 不存在时才设置（SETNX），通常用于分布式锁
     *
     * <p><b>注意：释放锁请使用 {@link #releaseLock(String, Object)}，严禁直接调用
     * {@link #delete(String...)}，否则可能误删其他线程持有的锁。</b></p>
     *
     * @return true 设置成功（即之前不存在该 key）
     * @throws IllegalArgumentException 如果 key 为空
     */
    public boolean setIfAbsent(String key, Object value, long seconds) {
        requireNonEmptyKey(key);
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, value, seconds, TimeUnit.SECONDS));
    }

    /**
     * 安全释放分布式锁（Lua 脚本原子校验 + 删除）
     * <p>仅当 key 对应的 value 与 expectedValue 一致时才删除，防止误释放他人的锁。</p>
     *
     * @param key           锁的 key
     * @param expectedValue 加锁时写入的 value（通常为 UUID 或线程标识）
     * @return true 释放成功
     * @throws IllegalArgumentException 如果 key 为空
     */
    public boolean releaseLock(String key, Object expectedValue) {
        requireNonEmptyKey(key);
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(key),
                String.valueOf(expectedValue));
        return result != null && result == 1L;
    }

    /**
     * 自增（步长为 1）
     */
    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增（指定步长）
     */
    public Long incrBy(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自减（步长为 1）
     */
    public Long decr(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

    /**
     * 自减（指定步长）
     */
    public Long decrBy(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    // ============ Hash 操作 ============

    /**
     * 获取 Hash 中某个字段的值
     */
    public Object hGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 获取 Hash 全部字段和值
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * 设置 Hash 中某个字段的值
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * 批量设置 Hash 字段
     */
    public void hSetAll(String key, Map<String, Object> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 删除 Hash 中一个或多个字段
     */
    public void hDelete(String key, String... fields) {
        redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    /**
     * 判断 Hash 中某个字段是否存在
     */
    public boolean hExists(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    /**
     * Hash 字段整数自增
     */
    public Long hIncrBy(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }

    // ============ List 操作 ============

    /**
     * 获取 List 指定范围的元素（0 到 -1 表示全部）
     */
    public List<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * 获取 List 长度
     */
    public Long lSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * 从左侧压入（LPUSH）
     */
    public Long lPush(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 从左侧批量压入（LPUSH 多个值）
     */
    public Long lPushAll(String key, Object... values) {
        return redisTemplate.opsForList().leftPushAll(key, values);
    }

    /**
     * 从右侧压入（RPUSH）
     */
    public Long rPush(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

    /**
     * 从右侧批量压入（RPUSH 多个值）
     */
    public Long rPushAll(String key, Object... values) {
        return redisTemplate.opsForList().rightPushAll(key, values);
    }

    /**
     * 从左侧弹出（LPOP）
     */
    public Object lPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * 从右侧弹出（RPOP）
     */
    public Object rPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    // ============ Set 操作 ============

    /**
     * 向 Set 中添加一个或多个元素
     */
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * 获取 Set 的全部元素
     */
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 判断元素是否在 Set 中
     */
    public boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * 从 Set 中移除一个或多个元素
     */
    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * 获取 Set 元素数量
     */
    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

    // ============ ZSet（有序集合）操作 ============

    /**
     * 向 ZSet 中添加元素及分数
     */
    public boolean zAdd(String key, Object value, double score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /**
     * 按排名升序获取 ZSet 元素（0 到 -1 表示全部）
     */
    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * 按分数范围获取 ZSet 元素（升序）
     */
    public Set<Object> zRangeByScore(String key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    /**
     * 获取元素在 ZSet 中的分数，元素不存在时返回 null
     */
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     * 获取元素在 ZSet 中的升序排名（0 为第一名），元素不存在时返回 null
     */
    public Long zRank(String key, Object value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    /**
     * 从 ZSet 中移除一个或多个元素
     */
    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * ZSet 元素分数自增
     */
    public Double zIncrScore(String key, Object value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    /**
     * 获取 ZSet 元素数量
     */
    public Long zSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    // ============ 内部校验 ============

    private static void requireNonEmptyKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Redis key must not be null or empty");
        }
    }
}