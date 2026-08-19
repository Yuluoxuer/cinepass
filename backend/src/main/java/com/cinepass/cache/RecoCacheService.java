package com.cinepass.cache;

import com.alibaba.fastjson2.JSON;
import com.cinepass.vo.RecoBaseVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 推荐共享底座缓存（Redisson 版）：物理 TTL + 单飞重载。
 *
 * <p>值以 fastjson2 JSON 字符串存入 {@link RBucket}（{@link StringCodec}），无逻辑过期包装；
 * 未命中时用 {@link RLock}（watchdog 自动续期，无锁超时问题）做单飞重载——赢家重载+写回，
 * 输家短等后重读，仍无则直接现算（Redis 故障降级，可用性不降）。</p>
 */
@Slf4j
@Service
public class RecoCacheService {

    public static final String BASE_KEY = "reco:base";
    public static final String BASE_LOCK_KEY = "reco:lock:base";

    private static final int LOSER_WAIT_MS = 100;

    private final RBucket<String> bucket;
    private final RLock lock;

    @Value("${ticket.reco-cache-ttl-seconds:600}")
    private int ttlSeconds;

    public RecoCacheService(RedissonClient redisson) {
        this.bucket = redisson.getBucket(BASE_KEY, StringCodec.INSTANCE);
        this.lock = redisson.getLock(BASE_LOCK_KEY);
    }

    /** 读底座缓存：命中返回；未命中单飞重载（赢家重载+写，输家短等后重读/现算） */
    public RecoBaseVO getOrRefresh(Supplier<RecoBaseVO> loader) {
        RecoBaseVO cached = safeParse(bucket.get());
        if (cached != null) {
            return cached;
        }
        boolean locked = false;
        try {
            // waitTime=0（抢不到立即放弃），leaseTime=-1（watchdog 自动续期，锁不会因超时失效）
            locked = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (locked) {
                RecoBaseVO fresh = loader.get();
                writeBase(fresh);
                return fresh;
            }
            // 输家：短等赢家写回后重读
            sleepQuietly(LOSER_WAIT_MS);
            RecoBaseVO afterWait = safeParse(bucket.get());
            if (afterWait != null) {
                return afterWait;
            }
            // 极端兜底：Redis 不可用或赢家失败，直接现算
            return loader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 写入底座缓存（物理 TTL） */
    public void writeBase(RecoBaseVO base) {
        bucket.set(JSON.toJSONString(base), ttlSeconds, TimeUnit.SECONDS);
    }

    /** 主动失效（影片创建/修改/状态翻转后调用） */
    public void invalidate() {
        bucket.delete();
    }

    /** 影片变更监听：失效底座缓存 */
    @EventListener
    public void onMovieChanged(MovieChangedEvent event) {
        invalidate();
    }

    private RecoBaseVO safeParse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return JSON.parseObject(json, RecoBaseVO.class);
        } catch (Exception e) {
            log.warn("reco:base 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
