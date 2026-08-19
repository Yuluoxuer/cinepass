package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.MovieMapper;
import com.cinepass.mapper.WantSeeMapper;
import com.cinepass.model.Movie;
import com.cinepass.service.WantSeeService;
import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.WantSeeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link WantSeeService} 实现。
 * <p>加入/取消幂等维护 movie.want_see_count；列表按想看时间顺序回填影片。
 */
@Service
public class WantSeeServiceImpl implements WantSeeService {

    private final WantSeeMapper wantSeeMapper;
    private final MovieMapper movieMapper;

    public WantSeeServiceImpl(WantSeeMapper wantSeeMapper, MovieMapper movieMapper) {
        this.wantSeeMapper = wantSeeMapper;
        this.movieMapper = movieMapper;
    }

    /** 加入想看；影片须存在；首次加入时 want_see_count +1（幂等） */
    @Override
    @Transactional
    public WantSeeVO add(String userId, String movieId) {
        if (!movieMapper.exists(movieId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影片不存在");
        }
        boolean before = wantSeeMapper.exists(userId, movieId);
        int n = wantSeeMapper.insertIgnore(userId, movieId);
        // 仅首次写入才 +1，重复点想看不重复计数
        if (!before && n > 0) {
            movieMapper.incrWantSeeCount(movieId, 1);
        }
        return WantSeeVO.builder().movieId(movieId).wanted(true).build();
    }

    /** 取消想看；实际删除成功时 want_see_count -1 */
    @Override
    @Transactional
    public WantSeeVO remove(String userId, String movieId) {
        int n = wantSeeMapper.delete(userId, movieId);
        // 实际删到行才 -1，避免重复取消把计数打穿
        if (n > 0) {
            movieMapper.incrWantSeeCount(movieId, -1);
        }
        return WantSeeVO.builder().movieId(movieId).wanted(false).build();
    }

    /** 分页查询用户想看电影列表，保持想看时间顺序 */
    @Override
    public PageResult<MovieVO> list(String userId, int page, int size) {
        // size 上限 50，与其它分页接口一致
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 50) size = 50;
        long total = wantSeeMapper.countByUser(userId);
        List<String> ids = wantSeeMapper.listMovieIds(userId, (page - 1) * size, size);
        if (ids == null || ids.isEmpty()) {
            return new PageResult<MovieVO>(Collections.<MovieVO>emptyList(), page, size, total);
        }
        List<Movie> movies = movieMapper.selectByIds(ids);
        Map<String, Movie> map = new HashMap<String, Movie>();
        if (movies != null) {
            for (Movie m : movies) {
                map.put(m.getMovieId(), m);
            }
        }
        // 按 ids 顺序组装，避免 IN 查询打乱排序
        List<MovieVO> items = new ArrayList<MovieVO>();
        for (String id : ids) {
            Movie m = map.get(id);
            if (m != null) {
                items.add(toVo(m));
            }
        }
        return new PageResult<MovieVO>(items, page, size, total);
    }

    /** Movie → MovieVO；genres_json 解析为列表 */
    private MovieVO toVo(Movie m) {
        List<String> genres = m.getGenresJson() == null
                ? Collections.<String>emptyList()
                : JSON.parseArray(m.getGenresJson(), String.class);
        return MovieVO.builder()
                .movieId(m.getMovieId())
                .title(m.getTitle())
                .posterUrl(m.getPosterUrl())
                .genres(genres)
                .rating(m.getRating())
                .durationMin(m.getDurationMin())
                .releaseDate(m.getReleaseDate() != null ? m.getReleaseDate().toString() : null)
                .status(m.getStatus())
                .description(m.getDescription())
                .cast(m.getCastText())
                .wantSeeCount(m.getWantSeeCount())
                .build();
    }
}
