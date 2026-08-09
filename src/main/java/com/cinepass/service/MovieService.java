package com.cinepass.service;

import com.cinepass.dto.MovieCreateDTO;
import com.cinepass.dto.MovieUpdateDTO;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;

import java.util.List;

/**
 * 电影查询与运营维护。
 */
public interface MovieService {

    /** 分页筛选；size 上限 50 */
    PageResult<MovieVO> page(String status, String q, String genre, int page, int size);

    /** 详情；不存在则 404 */
    MovieVO get(String movieId);

    /** 新建影片；status 默认 coming_soon */
    MovieVO create(MovieCreateDTO dto);

    /** 部分更新；字段 null 表示不改 */
    MovieVO update(String movieId, MovieUpdateDTO dto);

    /** 全部影片类型标签（按名称排序），来自 tag 字典表 */
    List<String> listGenres();
}
