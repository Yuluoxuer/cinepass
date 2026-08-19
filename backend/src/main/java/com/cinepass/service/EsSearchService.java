package com.cinepass.service;

import com.cinepass.vo.CinemaVO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * ES 搜索服务：封装 Elasticsearch 查询，用于影片/影院全文检索与排序。
 * <p>当前通过 {@link org.elasticsearch.client.RestHighLevelClient} 直连 ES 7.17，
 * ES 不可用时调用方应降级到 MySQL 查询路径。
 */
public interface EsSearchService {

    /**
     * 搜索影片。
     *
     * @param q      搜索关键词，非空时对 title^4 / cast^2 / director^2 / description 做 multi_match
     * @param status 状态筛选（hot_showing / coming_soon / off），可选
     * @param genre  类型筛选（如"动作"），可选，ES 内 term filter on genres keyword 数组
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 分页结果，MovieVO.posterUrl 可能未填充，需调用方后续补充
     */
    PageResult<MovieVO> searchMovies(String q, String status, String genre, int page, int size);

    /**
     * 搜索影院。
     *
     * @param q       搜索关键词，非空时对 name^4 / address 做 multi_match
     * @param movieId 按影片筛选（查询某影片在哪些影院有排片），可选，ES 内 term filter
     * @param lat     用户纬度（GCJ-02），配合 sort=distance 使用
     * @param lng     用户经度（GCJ-02），配合 sort=distance 使用
     * @param sort    排序方式：distance（按距离升序，需 lat/lng）、price（按最低票价升序）、不传则按 _score
     * @param page    页码，从 1 开始
     * @param size    每页条数
     * @return 分页结果，CinemaVO.cityName/trafficNote 可能未填充，需调用方后续补充
     */
    PageResult<CinemaVO> searchCinemas(String q, String movieId, BigDecimal lat, BigDecimal lng,
                                       Integer radiusMeters, String sort, int page, int size);

    /**
     * 搜索联想（Completion Suggester）。
     * <p>在 movie 和 cinema 索引的 completion 字段上做前缀匹配，
     * 返回去重后的候选词文本数组，用于前端搜索框实时补全。
     *
     * @param prefix 用户输入的前缀
     * @param size   返回条数上限
     * @return 候选词文本列表，ES 不可用时返回空列表（静默降级）
     */
    List<String> suggest(String prefix, int size);
}
