package com.cinepass.config;

import com.cinepass.service.EsIndexService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Elasticsearch 启动同步与定时对账测试。 */
class ElasticsearchIndexInitializerTest {

    @Test
    void scheduledReconcileRetriesFullDatabaseSync() {
        EsIndexService esIndexService = mock(EsIndexService.class);
        ElasticsearchIndexInitializer initializer = new ElasticsearchIndexInitializer(esIndexService);

        initializer.initialize();
        initializer.reconcile();

        verify(esIndexService, times(2)).initializeIndicesAndSync();
    }
}
