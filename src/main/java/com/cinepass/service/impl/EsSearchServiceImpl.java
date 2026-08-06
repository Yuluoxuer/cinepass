package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.service.EsSearchService;
import com.cinepass.vo.CastMemberVO;
import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * {@link EsSearchService} 实现：通过 RestHighLevelClient 直连 ES 7.17 执行搜索。
 */
@Service
public class EsSearchServiceImpl implements EsSearchService {

    private static final Logger log = LoggerFactory.getLogger(EsSearchServiceImpl.class);

    private static final String MOVIE_INDEX = "movie";
    private static final String CINEMA_INDEX = "cinema";

    private final RestHighLevelClient esClient;

    public EsSearchServiceImpl(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    @Override
    public PageResult<MovieVO> searchMovies(String q, String status, String genre, int page, int size) {
        int from = (page - 1) * size;

        SearchSourceBuilder source = new SearchSourceBuilder();
        BoolQueryBuilder bool = QueryBuilders.boolQuery();

        // 全文搜索或 match_all
        if (StringUtils.hasText(q)) {
            // 字段 boost 用 field(name, boost) 设置：
            // 字符串内嵌 boost（如 "title^4"）会被序列化成 "title^4^1.0" 双 boost，ES 解析抛 number_format_exception
            MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(q);
            multiMatch.field("title", 4.0f)
                    .field("title.ngram", 1.0f)
                    .field("cast", 2.0f)
                    .field("cast.ngram", 0.5f)
                    .field("description");
            bool.must(multiMatch);
        } else {
            bool.must(QueryBuilders.matchAllQuery());
        }

        // filter：状态
        if (StringUtils.hasText(status)) {
            bool.filter(QueryBuilders.termQuery("status", status));
        }
        // filter：类型标签（keyword 数组，term 匹配任一元素即命中）
        if (StringUtils.hasText(genre)) {
            bool.filter(QueryBuilders.termQuery("genres", genre));
        }

        source.query(bool);
        source.from(from);
        source.size(size);

        // q 为空时按上映日期 + 评分降序
        if (!StringUtils.hasText(q)) {
            source.sort("releaseDate", SortOrder.DESC);
            source.sort("rating", SortOrder.DESC);
        }

        SearchRequest request = new SearchRequest(MOVIE_INDEX).source(source);
        log.debug("ES movie search: {}", source);

        try {
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            List<MovieVO> items = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                items.add(hitToMovieVO(hit));
            }
            long total = response.getHits().getTotalHits() != null
                    ? response.getHits().getTotalHits().value : 0;
            return new PageResult<>(items, page, size, total);
        } catch (Exception e) {
            log.error("ES movie search failed: q={}, status={}", q, status, e);
            throw new BusinessException(ResultCode.FAIL, "ES 影片搜索失败");
        }
    }

    /** 将 ES hit 转为 MovieVO；posterUrl/nextShowDate 需调用方后续补充 */
    @SuppressWarnings("unchecked")
    private MovieVO hitToMovieVO(SearchHit hit) {
        Map<String, Object> src = hit.getSourceAsMap();
        if (src == null) {
            return MovieVO.builder().movieId(hit.getId()).build();
        }

        // genres: keyword 数组 → List<String>
        Object genresObj = src.get("genres");
        List<String> genres = Collections.emptyList();
        if (genresObj instanceof List) {
            genres = (List<String>) genresObj;
        }

        // rating: ES 返回 double/null
        BigDecimal rating = toBigDecimal(src.get("rating"));

        // durationMin: 整数
        Integer durationMin = toInteger(src.get("durationMin"));

        // wantSeeCount: 整数
        Integer wantSeeCount = toInteger(src.get("wantSeeCount"));

        // cast 文本 → 解析为结构化 castMembers
        String castText = (String) src.get("cast");
        List<CastMemberVO> castMembers = parseCastMembers(castText);

        return MovieVO.builder()
                .movieId(hit.getId())
                .title((String) src.get("title"))
                .genres(genres)
                .rating(rating)
                .durationMin(durationMin)
                .releaseDate((String) src.get("releaseDate"))
                .status((String) src.get("status"))
                .description((String) src.get("description"))
                .cast(castText)
                .castMembers(castMembers)
                .wantSeeCount(wantSeeCount)
                .build();
    }

    @Override
    public PageResult<CinemaVO> searchCinemas(String q, String movieId, BigDecimal lat, BigDecimal lng,
                                            Integer radiusMeters, String sort, int page, int size) {
        int from = (page - 1) * size;

        SearchSourceBuilder source = new SearchSourceBuilder();
        BoolQueryBuilder bool = QueryBuilders.boolQuery();

        // 全文搜索或 match_all
        if (StringUtils.hasText(q)) {
            MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(q);
            multiMatch.field("name", 4.0f)
                    .field("name.ngram", 1.0f)
                    .field("address");
            bool.must(multiMatch);
        } else {
            bool.must(QueryBuilders.matchAllQuery());
        }

        // filter：按影片筛选（ES 内 movieIds keyword 数组）
        if (StringUtils.hasText(movieId)) {
            bool.filter(QueryBuilders.termQuery("movieIds", movieId));
        }
        if (radiusMeters != null && lat != null && lng != null) {
            bool.filter(QueryBuilders.geoDistanceQuery("location")
                    .point(lat.doubleValue(), lng.doubleValue())
                    .distance(radiusMeters, DistanceUnit.METERS));
        }

        source.query(bool);
        source.from(from);
        source.size(size);

        // 排序
        if ("distance".equals(sort) && lat != null && lng != null) {
            // script_sort 基于 geo_point 计算弧距（米）
            Map<String, Object> params = new java.util.HashMap<>();
            params.put("lat", lat.doubleValue());
            params.put("lng", lng.doubleValue());
            Script script = new Script(ScriptType.INLINE, "painless",
                    "doc['location'].arcDistance(params.lat, params.lng)", params);
            source.sort(SortBuilders.scriptSort(script, ScriptSortBuilder.ScriptSortType.NUMBER)
                    .order(SortOrder.ASC));
        } else if ("price".equals(sort)) {
            source.sort("minPrice", SortOrder.ASC);
        }
        // 默认按 _score 降序，ES 默认行为，无需显式设置

        SearchRequest request = new SearchRequest(CINEMA_INDEX).source(source);
        log.debug("ES cinema search: {}", source);

        try {
            SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
            List<CinemaVO> items = new ArrayList<>();
            for (SearchHit hit : response.getHits()) {
                items.add(hitToCinemaVO(hit, "distance".equals(sort)));
            }
            long total = response.getHits().getTotalHits() != null
                    ? response.getHits().getTotalHits().value : 0;
            return new PageResult<>(items, page, size, total);
        } catch (Exception e) {
            log.error("ES cinema search failed: q={}, movieId={}", q, movieId, e);
            throw new BusinessException(ResultCode.FAIL, "ES 影院搜索失败");
        }
    }

    /** 将 ES hit 转为 CinemaVO；cityName/trafficNote 需调用方后续补充 */
    @SuppressWarnings("unchecked")
    private CinemaVO hitToCinemaVO(SearchHit hit, boolean distanceSort) {
        Map<String, Object> src = hit.getSourceAsMap();
        if (src == null) {
            return CinemaVO.builder().cinemaId(hit.getId()).build();
        }

        // minPrice
        BigDecimal minPrice = toBigDecimal(src.get("minPrice"));

        // features: keyword 数组
        Object featuresObj = src.get("features");
        List<String> features = Collections.emptyList();
        if (featuresObj instanceof List) {
            features = (List<String>) featuresObj;
        }

        // tags: keyword 数组
        Object tagsObj = src.get("tags");
        List<String> tags = Collections.emptyList();
        if (tagsObj instanceof List) {
            tags = (List<String>) tagsObj;
        }

        // 从 geo_point 提取 lat/lng；ES 返回格式为 Map{lat=xxx, lon=xxx}
        BigDecimal lat = null;
        BigDecimal lng = null;
        Object locationObj = src.get("location");
        if (locationObj instanceof Map) {
            Map<String, Object> locMap = (Map<String, Object>) locationObj;
            lat = toBigDecimal(locMap.get("lat"));
            lng = toBigDecimal(locMap.get("lon"));
        }

        BigDecimal distanceMeters = null;
        if (distanceSort && hit.getSortValues().length > 0) {
            distanceMeters = toBigDecimal(hit.getSortValues()[0]);
        }

        return CinemaVO.builder()
                .cinemaId(hit.getId())
                .name((String) src.get("name"))
                .address((String) src.get("address"))
                .cityId((String) src.get("cityId"))
                .lat(lat)
                .lng(lng)
                .distanceMeters(distanceMeters)
                .minPrice(minPrice)
                .features(features)
                .tags(tags)
                .build();
    }

    /** 安全转为 BigDecimal，保留 1 位小数 */
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return ((BigDecimal) value).setScale(1, RoundingMode.HALF_UP);
        if (value instanceof Double) return BigDecimal.valueOf((Double) value).setScale(1, RoundingMode.HALF_UP);
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(1, RoundingMode.HALF_UP);
        try {
            return new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /** 安全转为 Integer */
    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将逗号/斜杠分隔的演职员文本解析为结构化 CastMemberVO 列表。
     * 当前 DB 无 role 信息，role 字段为 null。
     */
    private static List<CastMemberVO> parseCastMembers(String castText) {
        if (!StringUtils.hasText(castText)) {
            return Collections.emptyList();
        }
        List<CastMemberVO> result = new ArrayList<>();
        // 按 "/"或"、"或","分割
        String[] parts = castText.split("\\s*[/、,，]\\s*");
        for (String part : parts) {
            String name = part.trim();
            if (!name.isEmpty()) {
                result.add(CastMemberVO.builder().name(name).build());
            }
        }
        return result;
    }

    @Override
    public List<String> suggest(String prefix, int size) {
        if (!StringUtils.hasText(prefix)) {
            return Collections.emptyList();
        }

        // ES 7.x Completion Suggester 请求格式
        Map<String, Object> completionOptions = new LinkedHashMap<>();
        completionOptions.put("field", "suggest");
        completionOptions.put("size", size);
        completionOptions.put("skip_duplicates", true);

        Map<String, Object> suggestSection = new LinkedHashMap<>();
        suggestSection.put("prefix", prefix);
        suggestSection.put("completion", completionOptions);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("suggest", Collections.singletonMap("search_suggest", suggestSection));
        root.put("_source", false);

        String json = JSON.toJSONString(root);
        log.debug("ES suggest request: {}", json);

        try {
            Request request = new Request("POST", "/movie,cinema/_search");
            request.setJsonEntity(json);
            Response response = esClient.getLowLevelClient().performRequest(request);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            // 从 suggest.search_suggest[0].options[].text 提取候选词
            JSONObject respJson = JSON.parseObject(responseBody);
            JSONObject suggestObj = respJson.getJSONObject("suggest");
            if (suggestObj == null) {
                return Collections.emptyList();
            }
            JSONArray suggestArray = suggestObj.getJSONArray("search_suggest");
            if (suggestArray == null || suggestArray.isEmpty()) {
                return Collections.emptyList();
            }
            JSONObject firstSuggest = suggestArray.getJSONObject(0);
            JSONArray options = firstSuggest.getJSONArray("options");
            if (options == null || options.isEmpty()) {
                return Collections.emptyList();
            }

            // 使用 LinkedHashSet 保持顺序并去重（跨索引可能存在重复）
            LinkedHashSet<String> resultSet = new LinkedHashSet<>();
            for (int i = 0; i < options.size(); i++) {
                JSONObject option = options.getJSONObject(i);
                String text = option.getString("text");
                if (text != null && !text.isEmpty()) {
                    resultSet.add(text);
                }
            }
            return new ArrayList<>(resultSet);
        } catch (Exception e) {
            // ES 不可用时静默降级，返回空列表
            log.warn("ES suggest failed, returning empty: prefix={}", prefix, e);
            return Collections.emptyList();
        }
    }
}
