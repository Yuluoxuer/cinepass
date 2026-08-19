package com.cinepass.service;

import com.cinepass.vo.MovieVO;
import com.cinepass.vo.PageResult;
import com.cinepass.vo.WantSeeVO;

/**
 * 想看业务：加入 / 取消、分页列表，并同步维护影片 want_see_count。
 */
public interface WantSeeService {

    /** 加入想看；影片须存在；首次加入时计数 +1（幂等） */
    WantSeeVO add(String userId, String movieId);

    /** 取消想看；实际删除成功时计数 -1 */
    WantSeeVO remove(String userId, String movieId);

    /** 分页查询用户想看电影列表，保持想看时间顺序 */
    PageResult<MovieVO> list(String userId, int page, int size);
}
