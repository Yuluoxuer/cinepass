package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量创建场次入参。
 *
 * <pre>
 * 按日期范围（dateStart ~ dateEnd）+ 每日时段（timeStart ~ timeEnd）
 * + 场次间隔（intervalMin &ge; 影片时长）生成场次列表。
 * </pre>
 */
@Data
public class ShowBatchCreateDTO {

    @NotBlank
    private String movieId;

    @NotBlank
    private String cinemaId;

    @NotBlank
    private String hallId;

    /** 开始日期（含） yyyy-MM-dd */
    @NotBlank
    private String dateStart;

    /** 结束日期（含） yyyy-MM-dd */
    @NotBlank
    private String dateEnd;

    /** 每日首场开场时间 HH:mm */
    @NotBlank
    private String timeStart;

    /** 每日末场散场不晚于 HH:mm */
    @NotBlank
    private String timeEnd;

    /**
     * 场次间隔（分钟）。两场之间（上一场开场到下一场开场）至少间隔此分钟数。
     * 不得小于影片时长，否则同厅冲突。
     */
    @NotNull
    private Integer intervalMin;

    /** 分区价；优先于 {@link #price} */
    private List<ShowCreateDTO.ZonePriceItem> zonePrices;

    /** 统一价兜底 */
    private BigDecimal price;
}
