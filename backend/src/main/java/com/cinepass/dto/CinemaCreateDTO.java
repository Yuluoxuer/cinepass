package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 新建影院入参（admin）。
 */
@Data
public class CinemaCreateDTO {

    /** 可选；空则服务端生成 {@code c + UUID7} */
    @Size(max = 32)
    private String cinemaId;

    /** 城市 ID；空则默认 {@code city_sh} */
    @Size(max = 64)
    private String cityId;

    @NotBlank(message = "城市名称不能为空")
    @Size(max = 64)
    private String cityName;

    @NotBlank(message = "影院名称不能为空")
    @Size(max = 128)
    private String name;

    @NotBlank(message = "影院地址不能为空")
    @Size(max = 255)
    private String address;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal lat;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal lng;

    @Size(max = 500)
    private String trafficNote;

    /** 标签列表，落库为 JSON 数组 */
    private List<@Size(max = 32) String> tags;
}
