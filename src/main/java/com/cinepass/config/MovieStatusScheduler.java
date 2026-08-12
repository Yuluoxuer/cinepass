package com.cinepass.config;

import com.cinepass.cache.MovieChangedEvent;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.service.EsIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 到上映日自动上架：周期性把 {@code release_date <= today} 的待映影片翻为热映。
 * <p>筛选条件基于相对日期，天然幂等且可补扫：漏跑一轮不会丢数据，下一轮会把期间到期的影片
 * 一次性全部补上；重复执行无副作用。翻转后逐个同步 ES 索引，个别同步失败由
 * {@link ElasticsearchIndexInitializer} 的周期全量对账兜底。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ticket.movie-status-scan-enabled", havingValue = "true", matchIfMissing = true)
public class MovieStatusScheduler {

    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");

    private final MovieMapper movieMapper;
    private final EsIndexService esIndexService;
    private final ApplicationEventPublisher eventPublisher;

    public MovieStatusScheduler(MovieMapper movieMapper, EsIndexService esIndexService,
                                ApplicationEventPublisher eventPublisher) {
        this.movieMapper = movieMapper;
        this.esIndexService = esIndexService;
        this.eventPublisher = eventPublisher;
    }

    /** 定时入口：默认每 30 分钟扫一次（可用 {@code ticket.movie-status-scan-interval-ms} 覆盖） */
    @Scheduled(fixedDelayString = "${ticket.movie-status-scan-interval-ms:1800000}")
    public void scheduledFlip() {
        int n = runOnce(LocalDate.now(CN));
        if (n > 0) {
            log.info("到上映日自动上架完成，{} 部待映影片转为热映", n);
        }
    }

    /** 单次扫描：翻转到期影片并逐个同步 ES（便于测试与手动触发） */
    public int runOnce(LocalDate today) {
        List<String> dueIds = movieMapper.selectReleasedButComingSoon(today);
        if (dueIds == null || dueIds.isEmpty()) {
            return 0;
        }
        int flipped = movieMapper.flipComingSoonToShowing(today);
        int esFailed = 0;
        for (String movieId : dueIds) {
            try {
                esIndexService.syncMovie(movieId);
            } catch (Exception e) {
                // 防御性兜底：生产实现 syncMovie 内部已吞异常并记 warn，此处仅防未来实现变化；
                // 个别失败由 ElasticsearchIndexInitializer 周期全量对账收敛
                esFailed++;
                log.error("影片 {} 上架后同步 ES 失败: {}", movieId, e.getMessage(), e);
            }
        }
        if (esFailed > 0) {
            log.warn("本次上架 {} 部影片，其中 {} 部 ES 同步失败（由周期全量对账收敛）", flipped, esFailed);
        }
        if (flipped > 0) {
            // 状态变化会影响推荐候选/新鲜度，失效共享底座缓存
            eventPublisher.publishEvent(new MovieChangedEvent(dueIds.get(0)));
        }
        return flipped;
    }
}
