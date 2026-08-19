package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 更新影院入参（部分字段；null 表示不改）。
 */
@Data
public class CinemaUpdateDTO {

    @Size(max = 64)
    private String cityId;

    @Size(min = 1, max = 64)
    private String cityName;

    @Size(min = 1, max = 128)
    private String name;

    @Size(min = 1, max = 255)
    private String address;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal lat;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal lng;

    @Size(max = 500)
    private String trafficNote;

    /** 非 null 时整体替换标签 JSON */
    private List<@Size(max = 32) String> tags;
}
