package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.cinepass.service.EsIndexService;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestHighLevelClient;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link EsIndexService} 实现。
 */
@Service
public class EsIndexServiceImpl implements EsIndexService {

    private static final Logger log = LoggerFactory.getLogger(EsIndexServiceImpl.class);
    private static final String MOVIE_INDEX = "movie";
    private static final String CINEMA_INDEX = "cinema";
    /**
     * 当前 mapping 版本号。映射结构有变更时递增：
     * 已存在的旧索引（含无版本标记的历史索引）会在启动/对账时被删除重建，
     * 避免 ES 侧 mapping 与代码不一致导致搜索/联想失效。
     */
    static final int MAPPING_VERSION = 2;
    /** 版本标记存放于索引 mapping 的 _meta 中 */
    private static final String MAPPING_VERSION_KEY = "cinepass_mapping_version";
    private final RestHighLevelClient esClient;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final ShowMapper showMapper;
    private final boolean syncEnabled;

    public EsIndexServiceImpl(RestHighLevelClient esClient,
                              MovieMapper movieMapper,
                              CinemaMapper cinemaMapper,
                              ShowMapper showMapper,
                              @Value("${search.elasticsearch.sync-enabled:true}") boolean syncEnabled) {
        this.esClient = esClient;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.showMapper = showMapper;
        this.syncEnabled = syncEnabled;
    }

    @Override
    public void initializeIndicesAndSync() {
        if (!syncEnabled) return;
        try {
            ensureIndex(MOVIE_INDEX, movieMapping());
            ensureIndex(CINEMA_INDEX, cinemaMapping());

            StringBuilder bulk = new StringBuilder();
            List<String> movieIds = new ArrayList<>();
            List<Movie> movies = movieMapper.listAll();
            if (movies != null) {
                for (Movie movie : movies) {
                    movieIds.add(movie.getMovieId());
                    appendBulkDocument(bulk, MOVIE_INDEX, movie.getMovieId(), movieDocument(movie));
                }
            }
            List<String> cinemaIds = new ArrayList<>();
            List<Cinema> cinemas = cinemaMapper.listAllForSearch();
            if (cinemas != null) {
                for (Cinema cinema : cinemas) {
                    cinemaIds.add(cinema.getCinemaId());
                    appendBulkDocument(bulk, CINEMA_INDEX, cinema.getCinemaId(), cinemaDocument(cinema));
                }
            }
            sendBulk(bulk);
            deleteStaleDocuments(MOVIE_INDEX, movieIds);
            deleteStaleDocuments(CINEMA_INDEX, cinemaIds);
            log.info("ES 索引初始化完成：movies={}, cinemas={}",
                    movies == null ? 0 : movies.size(), cinemas == null ? 0 : cinemas.size());
        } catch (Exception e) {
            log.warn("ES 索引初始化失败，搜索请求将降级到 MySQL", e);
        }
    }

    @Override
    public void syncMovie(String movieId) {
        if (!syncEnabled) return;
        executeAfterCommit(() -> syncMovieNow(movieId));
    }

    /** 在数据库事务提交后读取并同步影片，避免回滚事务产生幽灵文档。 */
    private void syncMovieNow(String movieId) {
        try {
            Movie movie = movieMapper.selectById(movieId);
            if (movie != null) {
                StringBuilder bulk = new StringBuilder();
                appendBulkDocument(bulk, MOVIE_INDEX, movieId, movieDocument(movie));
                sendBulk(bulk);
            }
        } catch (Exception e) {
            log.warn("ES 影片增量同步失败：movieId={}", movieId, e);
        }
    }

    @Override
    public void syncCinema(String cinemaId) {
        if (!syncEnabled) return;
        executeAfterCommit(() -> syncCinemaNow(cinemaId));
    }

    /** 在数据库事务提交后读取并同步影院聚合文档。 */
    private void syncCinemaNow(String cinemaId) {
        try {
            Cinema cinema = cinemaMapper.selectForSearchById(cinemaId);
            if (cinema != null) {
                StringBuilder bulk = new StringBuilder();
                appendBulkDocument(bulk, CINEMA_INDEX, cinemaId, cinemaDocument(cinema));
                sendBulk(bulk);
            }
        } catch (Exception e) {
            log.warn("ES 影院增量同步失败：cinemaId={}", cinemaId, e);
        }
    }

    @Override
    public void deleteCinema(String cinemaId) {
        if (!syncEnabled) return;
        executeAfterCommit(() -> deleteCinemaNow(cinemaId));
    }

    /** 在数据库事务提交后删除影院文档。 */
    private void deleteCinemaNow(String cinemaId) {
        try {
            perform("DELETE", "/" + CINEMA_INDEX + "/_doc/" + cinemaId, null);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                log.warn("ES 影院文档删除失败：cinemaId={}", cinemaId, e);
            }
        } catch (Exception e) {
            log.warn("ES 影院文档删除失败：cinemaId={}", cinemaId, e);
        }
    }

    /** 有活动事务时注册 afterCommit；无事务调用场景则立即执行。 */
    private void executeAfterCommit(final Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    /**
     * 确保索引按当前代码 mapping 存在：
     * 索引不存在时直接创建；已存在但 mapping 版本不匹配时删除重建。
     * 重建后的数据由 {@link #initializeIndicesAndSync()} 中的全量同步从数据库回填。
     */
    private void ensureIndex(String index, String mapping) throws IOException {
        if (!indexExists(index)) {
            perform("PUT", "/" + index, mapping);
            return;
        }
        int currentVersion = readMappingVersion(index);
        if (currentVersion != MAPPING_VERSION) {
            log.warn("ES 索引 {} mapping 版本 {} 与期望版本 {} 不一致，删除重建（数据待全量同步重建）",
                    index, currentVersion, MAPPING_VERSION);
            perform("DELETE", "/" + index, null);
            perform("PUT", "/" + index, mapping);
        }
    }

    /** HEAD 探测索引是否存在；ES 不可达时抛出，交由上层统一降级 */
    private boolean indexExists(String index) throws IOException {
        try {
            Response response = perform("HEAD", "/" + index, null);
            return response.getStatusLine().getStatusCode() == 200;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** 读取索引 mapping 的 _meta 版本号；无 _meta 或版本非数字时返回 -1 */
    private int readMappingVersion(String index) throws IOException {
        Response response = perform("GET", "/" + index + "/_mapping", null);
        String body = EntityUtils.toString(response.getEntity(), "UTF-8");
        JSONObject root = JSON.parseObject(body);
        if (root == null || root.getJSONObject(index) == null) {
            return -1;
        }
        JSONObject mappings = root.getJSONObject(index).getJSONObject("mappings");
        if (mappings == null) {
            return -1;
        }
        JSONObject meta = mappings.getJSONObject("_meta");
        if (meta == null) {
            return -1;
        }
        Integer version = meta.getInteger(MAPPING_VERSION_KEY);
        return version == null ? -1 : version;
    }

    /** 完整 bulk 成功后再删除数据库中已不存在的旧文档，避免同步失败留下空索引。 */
    private void deleteStaleDocuments(String index, List<String> activeIds) throws IOException {
        String query = activeIds.isEmpty()
                ? "{\"query\":{\"match_all\":{}}}"
                : "{\"query\":{\"bool\":{\"must_not\":{\"ids\":{\"values\":"
                + JSON.toJSONString(activeIds) + "}}}}}";
        perform("POST", "/" + index + "/_delete_by_query?conflicts=proceed&refresh=true",
                query);
    }

    /** 发送 NDJSON bulk 请求 */
    private void sendBulk(StringBuilder bulk) throws IOException {
        if (bulk.length() == 0) return;
        Request request = new Request("POST", "/_bulk?refresh=true");
        request.setEntity(new NStringEntity(bulk.toString(),
                ContentType.create("application/x-ndjson", "UTF-8")));
        Response response = esClient.getLowLevelClient().performRequest(request);
        String responseBody = response.getEntity() == null
                ? "{}" : EntityUtils.toString(response.getEntity(), "UTF-8");
        if (Boolean.TRUE.equals(JSON.parseObject(responseBody).getBoolean("errors"))) {
            throw new IOException("ES bulk request contains failed items");
        }
    }

    /** 追加一组 bulk action/document 行 */
    private static void appendBulkDocument(StringBuilder bulk, String index, String id,
                                           Map<String, Object> document) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_index", index);
        metadata.put("_id", id);
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("index", metadata);
        bulk.append(JSON.toJSONString(action)).append('\n');
        bulk.append(JSON.toJSONString(document)).append('\n');
    }

    /** 将数据库影片转换为 ES 文档 */
    private static Map<String, Object> movieDocument(Movie movie) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", movie.getTitle());
        document.put("description", movie.getDescription());
        document.put("cast", movie.getCastText());
        document.put("genres", parseStringList(movie.getGenresJson()));
        document.put("status", movie.getStatus());
        document.put("rating", movie.getRating());
        document.put("durationMin", movie.getDurationMin());
        document.put("releaseDate", movie.getReleaseDate() == null ? null : movie.getReleaseDate().toString());
        document.put("wantSeeCount", movie.getWantSeeCount());
        // completion suggest 字段：片名 + 演职员名
        List<String> suggestInputs = new ArrayList<>();
        if (movie.getTitle() != null && !movie.getTitle().isEmpty()) {
            suggestInputs.add(movie.getTitle());
        }
        String castText = movie.getCastText();
        if (castText != null && !castText.isEmpty()) {
            for (String part : castText.split("\\s*[/,，、]\\s*")) {
                String name = part.trim();
                if (!name.isEmpty()) {
                    suggestInputs.add(name);
                }
            }
        }
        Map<String, Object> suggest = new LinkedHashMap<>();
        suggest.put("input", suggestInputs);
        document.put("suggest", suggest);
        return document;
    }

    /** 将数据库影院与未来在售场次聚合为 ES 文档 */
    private Map<String, Object> cinemaDocument(Cinema cinema) {
        List<String> tags = parseStringList(cinema.getTagsJson());
        List<String> movieIds = new ArrayList<>();
        List<ShowSchedule> upcoming = showMapper.listEarliestUpcomingByCinema(
                cinema.getCinemaId(), OffsetDateTime.now());
        if (upcoming != null) {
            for (ShowSchedule show : upcoming) {
                if (show.getMovieId() != null) movieIds.add(show.getMovieId());
            }
        }

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("name", cinema.getName());
        document.put("address", cinema.getAddress());
        document.put("cityId", cinema.getCityId());
        if (cinema.getLat() != null && cinema.getLng() != null) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("lat", cinema.getLat());
            location.put("lon", cinema.getLng());
            document.put("location", location);
        }
        document.put("minPrice", cinema.getMinPrice());
        document.put("features", tags);
        document.put("tags", tags);
        document.put("movieIds", movieIds);
        // completion suggest 字段：影院名
        Map<String, Object> suggest = new LinkedHashMap<>();
        if (cinema.getName() != null && !cinema.getName().isEmpty()) {
            suggest.put("input", Collections.singletonList(cinema.getName()));
        }
        document.put("suggest", suggest);
        return document;
    }

    /** 解析数据库 JSON 字符串数组，坏数据按空列表处理 */
    private static List<String> parseStringList(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        try {
            return JSON.parseArray(json, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 执行低阶 ES 请求，避免索引管理与搜索查询相互耦合 */
    private Response perform(String method, String endpoint, String json) throws IOException {
        Request request = new Request(method, endpoint);
        if (json != null) request.setJsonEntity(json);
        return esClient.getLowLevelClient().performRequest(request);
    }

    /** ngram 分析器设置，用于 title/name 子字段模糊匹配 */
    private static final String NGRAM_SETTINGS =
            "\"settings\":{\"analysis\":{"
            + "\"analyzer\":{\"ngram_analyzer\":{\"tokenizer\":\"ngram_tokenizer\"}},"
            + "\"tokenizer\":{\"ngram_tokenizer\":{\"type\":\"ngram\",\"min_gram\":1,\"max_gram\":2}}"
            + "}},";

    /** 影片索引 mapping（含 ngram 子字段用于 title/cast 模糊匹配） */
    private static String movieMapping() {
        return "{" + NGRAM_SETTINGS
                + "\"mappings\":{\"_meta\":{\"" + MAPPING_VERSION_KEY + "\":" + MAPPING_VERSION + "},\"properties\":{"
                + "\"title\":{\"type\":\"text\",\"analyzer\":\"ik_smart\","
                + "\"fields\":{\"ngram\":{\"type\":\"text\",\"analyzer\":\"ngram_analyzer\"}}},"
                + "\"description\":{\"type\":\"text\",\"analyzer\":\"ik_smart\"},"
                + "\"cast\":{\"type\":\"text\",\"analyzer\":\"ik_smart\","
                + "\"fields\":{\"ngram\":{\"type\":\"text\",\"analyzer\":\"ngram_analyzer\"}}},"
                + "\"genres\":{\"type\":\"keyword\"},\"status\":{\"type\":\"keyword\"},"
                + "\"rating\":{\"type\":\"float\"},\"durationMin\":{\"type\":\"integer\"},"
                + "\"releaseDate\":{\"type\":\"date\"},\"wantSeeCount\":{\"type\":\"integer\"},"
                + "\"suggest\":{\"type\":\"completion\",\"analyzer\":\"ik_smart\"}"
                + "}}}";
    }

    /** 影院索引 mapping（含 ngram 子字段用于 name 模糊匹配） */
    private static String cinemaMapping() {
        return "{" + NGRAM_SETTINGS
                + "\"mappings\":{\"_meta\":{\"" + MAPPING_VERSION_KEY + "\":" + MAPPING_VERSION + "},\"properties\":{"
                + "\"name\":{\"type\":\"text\",\"analyzer\":\"ik_smart\","
                + "\"fields\":{\"ngram\":{\"type\":\"text\",\"analyzer\":\"ngram_analyzer\"}}},"
                + "\"address\":{\"type\":\"text\",\"analyzer\":\"ik_smart\"},"
                + "\"cityId\":{\"type\":\"keyword\"},\"location\":{\"type\":\"geo_point\"},"
                + "\"minPrice\":{\"type\":\"float\"},\"features\":{\"type\":\"keyword\"},"
                + "\"tags\":{\"type\":\"keyword\"},\"movieIds\":{\"type\":\"keyword\"},"
                + "\"suggest\":{\"type\":\"completion\",\"analyzer\":\"ik_smart\"}"
                + "}}}";
    }
}
