package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电影展示对象（列表/详情/想看列表共用）。
 */
@Data
@Builder
public class MovieVO {

    /** 电影 ID */
    private String movieId;

    /** 片名 */
    private String title;

    /** 海报 URL */
    private String posterUrl;

    /** 类型标签列表 */
    private List<String> genres;

    /** 评分 */
    private BigDecimal rating;

    /** 片长（分钟） */
    private Integer durationMin;

    /** 上映日期（yyyy-MM-dd） */
    private String releaseDate;

    /** 上映状态，如 upcoming / showing / ended */
    private String status;

    /** 剧情简介 */
    private String description;

    /** 演职人员文案（逗号分隔） */
    private String cast;

    /** 导演姓名；ES 索引中有则返回，无则为 null */
    private String director;

    /** 结构化演职人员列表；数据来源 ES 或 DB cast_text 解析，无法解析时为空列表 */
    private List<CastMemberVO> castMembers;

    /** 想看人数 */
    private Integer wantSeeCount;

    /**
     * 最近一场排片日期（yyyy-MM-dd，Asia/Shanghai）。
     * 从 show_schedule 表聚合，无排片时为 null。
     */
    private String nextShowDate;
}
