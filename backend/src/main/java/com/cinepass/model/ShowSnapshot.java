package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 下单快照查询行：场次 + 影片/影院/厅名称。
 */
@Data
public class ShowSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 场次 ID */
    private String showId;

    /** 影片 ID */
    private String movieId;

    /** 影院 ID（运营按影院过滤用） */
    private String cinemaId;

    /** 影厅 ID */
    private String hallId;

    /** 开场时间 */
    private OffsetDateTime startTime;

    /** 场次基础票价；价区缺失时回退用 */
    private BigDecimal basePrice;

    /** 影片标题 */
    private String movieTitle;

    /** 影院名称 */
    private String cinemaName;

    /** 影厅名称 */
    private String hallName;
}
