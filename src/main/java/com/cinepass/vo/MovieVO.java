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

    /** 演职人员文案 */
    private String cast;

    /** 想看人数 */
    private Integer wantSeeCount;

    /**
     * 院→片场景：该影院下一场开映的本地日历日（yyyy-MM-dd，Asia/Shanghai）。
     * 其它列表接口一般为 null。
     */
    private String nextShowDate;
}
