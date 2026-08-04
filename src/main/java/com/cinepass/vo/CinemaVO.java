package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 影院对外展示对象（附近列表 / 详情共用）。
 */
@Data
@Builder
public class CinemaVO {

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

    /** 距用户距离（米）；未传经纬度时为 null */
    private BigDecimal distanceMeters;

    /** 当前最低票价；无排片时为 null */
    private BigDecimal minPrice;

    /** 交通说明；仅详情返回 */
    private String trafficNote;

    /** 标签列表；仅详情返回 */
    private List<String> tags;

    /** 下属影厅；仅详情返回 */
    private List<HallVO> halls;
}
