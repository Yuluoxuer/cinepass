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

    /** 下架：校验未来无在售场次后置为 off（有则抛 CONFLICT） */
    MovieVO takeDown(String movieId);

    /** 上架：按上映日期推导状态（今天已上映→热映，未上映→待映） */
    MovieVO relist(String movieId);

    /** 全部影片类型标签（按名称排序），来自 tag 字典表 */
    List<String> listGenres();
}
