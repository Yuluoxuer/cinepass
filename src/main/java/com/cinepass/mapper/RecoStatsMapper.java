package com.cinepass.mapper;

import com.cinepass.model.RecoStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 热门推荐物化统计表 {@code reco_stats} Mapper。
 */
@Mapper
public interface RecoStatsMapper {

    /** 查询全部影片的周信号物化行（含 hot_score） */
    List<RecoStats> selectAll();

    /** 统计已物化影片数（判断榜单是否为空） */
    long count();

    /** 最近一次重算时间；无数据返回 null */
    OffsetDateTime selectMaxComputedAt();

    /** 清空物化表（全量重算前） */
    int deleteAll();

    /** 批量插入重算结果 */
    int batchInsert(@Param("rows") List<RecoStats> rows);

    /** 近 7 天已出票订单按影片计数（order_ticket JOIN show_schedule 补 movie_id） */
    List<RecoStats> selectWeekOrderCounts(@Param("since") OffsetDateTime since);

    /** 近 7 天影片详情点击数按影片 SUM（reco_clicks） */
    List<RecoStats> selectWeekClickCounts(@Param("sinceDate") LocalDate sinceDate);
}
