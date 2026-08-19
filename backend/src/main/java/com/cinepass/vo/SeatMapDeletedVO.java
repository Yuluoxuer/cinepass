package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 删除座位图出参。
 */
@Data
@Builder
public class SeatMapDeletedVO {

    /** 是否已删除 */
    private boolean deleted;

    /** 被删除的座位图 ID */
    private String seatMapId;
}
