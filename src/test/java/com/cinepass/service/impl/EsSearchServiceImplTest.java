package com.cinepass.service.impl;

import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.PageResult;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EsSearchServiceImpl} 的 ES 响应转换测试。
 */
class EsSearchServiceImplTest {

    private HttpServer server;
    private RestHighLevelClient esClient;

    @AfterEach
    void tearDown() throws Exception {
        if (esClient != null) {
            esClient.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void distanceSortValueIsReturnedAsDistanceMeters() throws Exception {
        String response = "{"
                + "\"took\":1,\"timed_out\":false,"
                + "\"_shards\":{\"total\":1,\"successful\":1,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[{"
                + "\"_index\":\"cinema\",\"_type\":\"_doc\",\"_id\":\"cinema_test\",\"_score\":null,"
                + "\"_source\":{\"name\":\"测试影院\",\"address\":\"上海市测试路\","
                + "\"location\":{\"lat\":31.2304,\"lon\":121.4737}},\"sort\":[1250.4]}]}}";
        String info = "{\"name\":\"test-node\",\"cluster_name\":\"test-cluster\","
                + "\"cluster_uuid\":\"test-cluster-uuid\",\"version\":{"
                + "\"number\":\"7.17.28\",\"build_flavor\":\"default\",\"build_type\":\"tar\","
                + "\"build_hash\":\"test\",\"build_date\":\"2026-08-05T00:00:00.000Z\","
                + "\"build_snapshot\":false,\"lucene_version\":\"8.11.3\","
                + "\"minimum_wire_compatibility_version\":\"6.8.0\","
                + "\"minimum_index_compatibility_version\":\"6.0.0-beta1\"},"
                + "\"tagline\":\"You Know, for Search\"}";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = ("/".equals(exchange.getRequestURI().getPath()) ? info : response)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        esClient = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")));

        EsSearchServiceImpl service = new EsSearchServiceImpl(esClient);
        PageResult<CinemaVO> result = service.searchCinemas("测试", null,
                new BigDecimal("31.2304"), new BigDecimal("121.4737"),
                null, "distance", 1, 10);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getDistanceMeters())
                .isEqualByComparingTo("1250.4");
    }

    @Test
    void suggestReturnsCandidateTexts() throws Exception {
        String suggestResponse = "{"
                + "\"took\":5,\"timed_out\":false,"
                + "\"_shards\":{\"total\":2,\"successful\":2,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]},"
                + "\"suggest\":{\"search_suggest\":[{"
                + "\"text\":\"阿\",\"offset\":0,\"length\":1,\"options\":["
                + "{\"text\":\"阿凡达\",\"_index\":\"movie\",\"_id\":\"m001\",\"_score\":1.0},"
                + "{\"text\":\"阿甘正传\",\"_index\":\"movie\",\"_id\":\"m002\",\"_score\":0.9},"
                + "{\"text\":\"阿拉丁\",\"_index\":\"movie\",\"_id\":\"m003\",\"_score\":0.8}"
                + "]}]}}";
        String info = "{\"name\":\"test-node\",\"cluster_name\":\"test-cluster\","
                + "\"cluster_uuid\":\"test-cluster-uuid\",\"version\":{"
                + "\"number\":\"7.17.28\",\"build_flavor\":\"default\",\"build_type\":\"tar\","
                + "\"build_hash\":\"test\",\"build_date\":\"2026-08-05T00:00:00.000Z\","
                + "\"build_snapshot\":false,\"lucene_version\":\"8.11.3\","
                + "\"minimum_wire_compatibility_version\":\"6.8.0\","
                + "\"minimum_index_compatibility_version\":\"6.0.0-beta1\"},"
                + "\"tagline\":\"You Know, for Search\"}";
        startServer(info, suggestResponse);

        EsSearchServiceImpl service = new EsSearchServiceImpl(esClient);
        List<String> result = service.suggest("阿", 8);

        assertThat(result).containsExactly("阿凡达", "阿甘正传", "阿拉丁");
    }

    @Test
    void suggestEmptyPrefixReturnsEmpty() throws Exception {
        startServer("{}", "{}");
        EsSearchServiceImpl service = new EsSearchServiceImpl(esClient);

        assertThat(service.suggest("", 8)).isEmpty();
        assertThat(service.suggest(null, 8)).isEmpty();
    }

    @Test
    void suggestNoOptionsReturnsEmpty() throws Exception {
        String suggestResponse = "{"
                + "\"took\":3,\"timed_out\":false,"
                + "\"_shards\":{\"total\":2,\"successful\":2,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]},"
                + "\"suggest\":{\"search_suggest\":[{"
                + "\"text\":\"xyz\",\"offset\":0,\"length\":3,\"options\":[]"
                + "}]}}";
        startServer("{}", suggestResponse);

        EsSearchServiceImpl service = new EsSearchServiceImpl(esClient);
        List<String> result = service.suggest("xyz", 8);

        assertThat(result).isEmpty();
    }

    @Test
    void suggestEsUnavailableReturnsEmpty() throws Exception {
        // 服务未启动，ES 连接会失败，应静默降级返回空列表
        EsSearchServiceImpl service = new EsSearchServiceImpl(createUnreachableClient());
        List<String> result = service.suggest("阿", 8);

        assertThat(result).isEmpty();
    }

    @Test
    void suggestDeduplicatesAcrossIndices() throws Exception {
        // 模拟同一候选词出现在两个索引中的场景
        String suggestResponse = "{"
                + "\"took\":5,\"timed_out\":false,"
                + "\"_shards\":{\"total\":2,\"successful\":2,\"skipped\":0,\"failed\":0},"
                + "\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"max_score\":null,\"hits\":[]},"
                + "\"suggest\":{\"search_suggest\":[{"
                + "\"text\":\"万达\",\"offset\":0,\"length\":2,\"options\":["
                + "{\"text\":\"万达影城\",\"_index\":\"cinema\",\"_id\":\"c001\",\"_score\":1.0},"
                + "{\"text\":\"万达影城\",\"_index\":\"movie\",\"_id\":\"m099\",\"_score\":0.5}"
                + "]}]}}";
        String info = "{\"name\":\"test-node\",\"cluster_name\":\"test-cluster\","
                + "\"cluster_uuid\":\"test-cluster-uuid\",\"version\":{"
                + "\"number\":\"7.17.28\",\"build_flavor\":\"default\",\"build_type\":\"tar\","
                + "\"build_hash\":\"test\",\"build_date\":\"2026-08-05T00:00:00.000Z\","
                + "\"build_snapshot\":false,\"lucene_version\":\"8.11.3\","
                + "\"minimum_wire_compatibility_version\":\"6.8.0\","
                + "\"minimum_index_compatibility_version\":\"6.0.0-beta1\"},"
                + "\"tagline\":\"You Know, for Search\"}";
        startServer(info, suggestResponse);

        EsSearchServiceImpl service = new EsSearchServiceImpl(esClient);
        List<String> result = service.suggest("万达", 8);

        // 跨索引重复应只保留一条
        assertThat(result).containsExactly("万达影城");
    }

    @Test
    void multiMatchFieldBoostIsSerializedWithSingleBoost() {
        MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery("星际穿越");
        multiMatch.field("title", 4.0f)
                .field("title.ngram", 1.0f)
                .field("cast", 2.0f)
                .field("cast.ngram", 0.5f)
                .field("description");

        String json = multiMatch.toString();

        // 回归：字符串内嵌 boost（"title^4"）会被序列化成 "title^4^1.0" 双 boost，
        // ES 解析 "0.5^1.0" 抛 number_format_exception，导致搜索整体降级 MySQL
        assertThat(json).contains("title^4").doesNotContain("title^4^1.0");
        assertThat(json).contains("cast^2").doesNotContain("cast^2^1.0");
        assertThat(json).contains("cast.ngram^0.5").doesNotContain("cast.ngram^0.5^1.0");
    }

    private void startServer(String infoResponse, String searchResponse) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body;
            if ("/".equals(path)) {
                body = infoResponse.getBytes(StandardCharsets.UTF_8);
            } else {
                body = searchResponse.getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        esClient = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")));
    }

    /** 创建一个指向不可达端点的客户端，用于测试 ES 不可用时的降级行为 */
    private static RestHighLevelClient createUnreachableClient() {
        return new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", 1, "http")));
    }
}
