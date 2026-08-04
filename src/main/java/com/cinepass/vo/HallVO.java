package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 影厅对外展示对象。
 */
@Data
@Builder
public class HallVO {

    /** 影厅 ID */
    private String hallId;

    /** 所属影院 ID */
    private String cinemaId;

    /** 影厅名称 */
    private String name;

    /** 绑定的座位图 ID */
    private String seatMapId;

    /** 关联场次数（列表聚合；可空） */
    private Integer showCount;
}
