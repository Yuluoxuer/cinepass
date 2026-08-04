package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 影厅表 {@code hall} 映射。
 */
@Data
public class Hall implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 影厅 ID */
    private String hallId;

    /** 所属影院 ID */
    private String cinemaId;

    /** 影厅名称 */
    private String name;

    /** 绑定座位图 ID */
    private String seatMapId;

    /** 关联场次数（查询聚合；可空） */
    private Integer showCount;
}
