package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 智能选座入参（{@code POST /api/v1/reco/seats}）。
 */
@Data
public class RecommendSeatsDTO {

    /** 场次 ID */
    @NotBlank(message = "showId 不能为空")
    private String showId;

    /** 票数 1–4 */
    @NotNull(message = "count 不能为空")
    @Min(value = 1, message = "count 至少为 1")
    @Max(value = 4, message = "count 至多为 4")
    private Integer count;

    /** 排区偏好：front / middle / back；默认 middle */
    private String preferRow;

    /** 左右偏好：center / aisle / edge；默认 center */
    private String preferSide;

    /** 是否要求连座；默认 true */
    private Boolean together;
}
