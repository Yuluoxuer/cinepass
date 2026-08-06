package com.cinepass.service.impl;

import com.cinepass.mapper.CinemaMapper;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.ShowMapper;
import com.cinepass.model.Cinema;
import com.cinepass.model.Movie;
import com.cinepass.model.ShowSchedule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link EsIndexServiceImpl} 的索引初始化与全量同步测试。
 */
class EsIndexServiceImplTest {

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private RestHighLevelClient esClient;
    private boolean bulkErrors;
    /** 模拟索引已存在（HEAD 返回 200） */
    private boolean headReturnsExisting;
    /** 模拟既有索引 mapping 的 _meta 版本；-1 表示无版本标记 */
    private int existingMappingVersion = -1;

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
    void initializeCreatesMappingsAndBulkIndexesDatabaseRows() throws Exception {
        startFakeEs();
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        ShowMapper showMapper = mock(ShowMapper.class);

        Movie movie = new Movie();
        movie.setMovieId("movie_1");
        movie.setTitle("星际测试");
        movie.setGenresJson("[\"科幻\"]");
        movie.setReleaseDate(LocalDate.of(2026, 8, 5));
        movie.setStatus("hot_showing");
        movie.setWantSeeCount(10);
        when(movieMapper.listAll()).thenReturn(Collections.singletonList(movie));

        Cinema cinema = new Cinema();
        cinema.setCinemaId("cinema_1");
        cinema.setCityId("city_sh");
        cinema.setName("星际影城");
        cinema.setAddress("上海市测试路 1 号");
        cinema.setLat(new BigDecimal("31.2304"));
        cinema.setLng(new BigDecimal("121.4737"));
        cinema.setMinPrice(new BigDecimal("39.90"));
        cinema.setTagsJson("[\"IMAX\"]");
        when(cinemaMapper.listAllForSearch()).thenReturn(Collections.singletonList(cinema));

        ShowSchedule upcoming = new ShowSchedule();
        upcoming.setMovieId("movie_1");
        when(showMapper.listEarliestUpcomingByCinema(eq("cinema_1"), any()))
                .thenReturn(Collections.singletonList(upcoming));

        EsIndexServiceImpl service = new EsIndexServiceImpl(
                esClient, movieMapper, cinemaMapper, showMapper, true);
        service.initializeIndicesAndSync();

        assertThat(requests).extracting(RecordedRequest::methodAndPath)
                .contains("HEAD /movie", "PUT /movie", "HEAD /cinema", "PUT /cinema", "POST /_bulk");
        String allBodies = joinBodies();
        assertThat(allBodies).contains("ik_smart", "geo_point", "星际测试", "星际影城",
                "\"movieIds\":[\"movie_1\"]");
    }

    @Test
    void bulkItemFailureDoesNotDeleteExistingDocuments() throws Exception {
        bulkErrors = true;
        startFakeEs();
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        ShowMapper showMapper = mock(ShowMapper.class);

        Movie movie = new Movie();
        movie.setMovieId("movie_1");
        movie.setTitle("测试影片");
        when(movieMapper.listAll()).thenReturn(Collections.singletonList(movie));
        when(cinemaMapper.listAllForSearch()).thenReturn(Collections.emptyList());

        EsIndexServiceImpl service = new EsIndexServiceImpl(
                esClient, movieMapper, cinemaMapper, showMapper, true);
        service.initializeIndicesAndSync();

        assertThat(requests).extracting(RecordedRequest::methodAndPath)
                .contains("POST /_bulk")
                .doesNotContain("POST /movie/_delete_by_query", "POST /cinema/_delete_by_query");
    }

    @Test
    void staleMappingVersionIndexIsDeletedAndRecreated() throws Exception {
        headReturnsExisting = true;
        existingMappingVersion = 1;
        startFakeEs();
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        ShowMapper showMapper = mock(ShowMapper.class);

        Movie movie = new Movie();
        movie.setMovieId("movie_1");
        movie.setTitle("星际测试");
        when(movieMapper.listAll()).thenReturn(Collections.singletonList(movie));
        when(cinemaMapper.listAllForSearch()).thenReturn(Collections.emptyList());

        EsIndexServiceImpl service = new EsIndexServiceImpl(
                esClient, movieMapper, cinemaMapper, showMapper, true);
        service.initializeIndicesAndSync();

        // 版本过期 → 先读版本，再删除重建，最后全量回填
        assertThat(requests).extracting(RecordedRequest::methodAndPath)
                .contains("GET /movie/_mapping", "DELETE /movie", "PUT /movie",
                        "GET /cinema/_mapping", "DELETE /cinema", "PUT /cinema",
                        "POST /_bulk");
        assertThat(joinBodies()).contains("cinepass_mapping_version");
    }

    @Test
    void currentMappingVersionIndexIsKept() throws Exception {
        headReturnsExisting = true;
        existingMappingVersion = EsIndexServiceImpl.MAPPING_VERSION;
        startFakeEs();
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);

        Movie movie = new Movie();
        movie.setMovieId("movie_1");
        movie.setTitle("星际测试");
        when(movieMapper.listAll()).thenReturn(Collections.singletonList(movie));
        when(cinemaMapper.listAllForSearch()).thenReturn(Collections.emptyList());

        EsIndexServiceImpl service = new EsIndexServiceImpl(
                esClient, movieMapper, cinemaMapper, mock(ShowMapper.class), true);
        service.initializeIndicesAndSync();

        // 版本一致 → 只读版本号，不删除不重建，照常全量回填
        assertThat(requests).extracting(RecordedRequest::methodAndPath)
                .contains("GET /movie/_mapping", "GET /cinema/_mapping", "POST /_bulk")
                .doesNotContain("DELETE /movie", "PUT /movie", "DELETE /cinema", "PUT /cinema");
    }

    @Test
    void incrementalMovieSyncRunsOnlyAfterTransactionCommit() throws Exception {
        startFakeEs();
        MovieMapper movieMapper = mock(MovieMapper.class);
        Movie movie = new Movie();
        movie.setMovieId("movie_1");
        when(movieMapper.selectById("movie_1")).thenReturn(movie);
        EsIndexServiceImpl service = new EsIndexServiceImpl(esClient, movieMapper,
                mock(CinemaMapper.class), mock(ShowMapper.class), true);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.syncMovie("movie_1");
            verifyNoInteractions(movieMapper);

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(movieMapper).selectById("movie_1");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private void startFakeEs() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        esClient = new RestHighLevelClient(RestClient.builder(
                new HttpHost("127.0.0.1", server.getAddress().getPort(), "http")));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = readBody(exchange);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, body));
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Elastic-Product", "Elasticsearch");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(headReturnsExisting ? 200 : 404, -1);
            exchange.close();
            return;
        }
        if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/_mapping")) {
            String index = path.substring(1, path.indexOf("/_mapping"));
            String responseJson = existingMappingVersion == -1
                    ? "{\"" + index + "\":{\"mappings\":{\"properties\":{}}}}"
                    : "{\"" + index + "\":{\"mappings\":{\"_meta\":{\"cinepass_mapping_version\":"
                    + existingMappingVersion + "},\"properties\":{}}}}";
            byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
            return;
        }
        String responseJson = "/_bulk".equals(path)
                ? (bulkErrors ? "{\"errors\":true}" : "{\"errors\":false}")
                : "{}";
        byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = exchange.getRequestBody().read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private String joinBodies() {
        StringBuilder result = new StringBuilder();
        for (RecordedRequest request : requests) {
            result.append(request.body);
        }
        return result.toString();
    }

    private static final class RecordedRequest {
        private final String method;
        private final String path;
        private final String body;

        private RecordedRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }

        private String methodAndPath() {
            return method + " " + path;
        }
    }
}
