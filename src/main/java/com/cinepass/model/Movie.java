package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 电影表 {@code movie} 映射。
 */
@Data
public class Movie implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 电影 ID */
    private String movieId;

    /** 片名 */
    private String title;

    /** 海报 URL */
    private String posterUrl;

    /** 类型 JSON 数组字符串 */
    private String genresJson;

    /** 评分 */
    private BigDecimal rating;

    /** 片长（分钟） */
    private Integer durationMin;

    /** 上映日期 */
    private LocalDate releaseDate;

    /** 上映状态 */
    private String status;

    /** 剧情简介 */
    private String description;

    /** 演职人员文案 */
    private String castText;

    /** 想看人数计数 */
    private Integer wantSeeCount;
}
