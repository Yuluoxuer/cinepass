package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 影院表 {@code cinema} 映射。
 */
@Data
public class Cinema implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 影院 ID */
    private String cinemaId;

    /** 城市 ID */
    private String cityId;

    /** 城市名称 */
    private String cityName;

    /** 影院名称 */
    private String name;

    /** 地址 */
    private String address;

    /** 纬度 */
    private BigDecimal lat;

    /** 经度 */
    private BigDecimal lng;

    /** 交通说明 */
    private String trafficNote;

    /** 标签 JSON 数组字符串 */
    private String tagsJson;

    /** 距用户距离（米）；附近查询计算列，非表字段 */
    private BigDecimal distanceMeters;

    /** 当前最低票价；附近查询聚合列，非表字段 */
    private BigDecimal minPrice;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
