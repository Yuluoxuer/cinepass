package com.cinepass.config;

import com.cinepass.service.EsIndexService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后初始化并同步 Elasticsearch 搜索索引。
 */
@Component
@ConditionalOnProperty(name = "search.elasticsearch.sync-enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchIndexInitializer {

    private final EsIndexService esIndexService;

    public ElasticsearchIndexInitializer(EsIndexService esIndexService) {
        this.esIndexService = esIndexService;
    }

    /** 在数据库初始化任务完成后重建搜索索引 */
    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        esIndexService.initializeIndicesAndSync();
    }

    /**
     * 周期性全量对账，使短暂 ES 故障导致的漏同步可以自动恢复。
     * <p>间隔默认 30 分钟（可被 {@code search.elasticsearch.reconcile-interval-ms} 覆盖）：
     * 常规增删改已有增量同步兜底，过频的全量重建会在小内存 ES 上反复删光+重灌+refresh，
     * 加剧内存/IO 压力、造成搜索间歇性卡顿。
     */
    @Scheduled(
            initialDelayString = "${search.elasticsearch.reconcile-interval-ms:1800000}",
            fixedDelayString = "${search.elasticsearch.reconcile-interval-ms:1800000}")
    public void reconcile() {
        esIndexService.initializeIndicesAndSync();
    }
}
