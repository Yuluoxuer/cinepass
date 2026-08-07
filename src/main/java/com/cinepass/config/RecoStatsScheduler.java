package com.cinepass.config;

import com.cinepass.service.RecoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 推荐周信号物化调度：周期全量重算 {@code reco_stats}。
 * <p>GET /reco/weekly-hot 在物化为空或过期时也会同步重算兜底，调度器仅负责「保温」；
 * 单次失败仅记日志，下轮重跑。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ticket.reco-recompute-enabled", havingValue = "true", matchIfMissing = true)
public class RecoStatsScheduler {

    private final RecoService recoService;

    public RecoStatsScheduler(RecoService recoService) {
        this.recoService = recoService;
    }

    /** 定时入口：默认每 10 分钟（可用 {@code ticket.reco-recompute-interval-ms} 覆盖） */
    @Scheduled(fixedDelayString = "${ticket.reco-recompute-interval-ms:600000}")
    public void scheduledRecompute() {
        try {
            recoService.recomputeStats();
            log.info("推荐周信号物化重算完成");
        } catch (Exception e) {
            log.error("推荐周信号物化重算失败: {}", e.getMessage(), e);
        }
    }
}
