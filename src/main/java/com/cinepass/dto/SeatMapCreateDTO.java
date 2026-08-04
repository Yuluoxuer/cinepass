package com.cinepass.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 创建稀疏座位图入参。
 */
@Data
public class SeatMapCreateDTO {

    /** 可选；空则服务端生成 {@code sm + UUID7} */
    @Size(max = 32)
    private String seatMapId;

    /** 所属影院；admin 必填，staff 可省略 */
    private String cinemaId;

    @NotNull(message = "座位图行数不能为空")
    @Min(value = 1, message = "座位图行数必须大于 0")
    private Integer rows;

    @NotNull(message = "座位图列数不能为空")
    @Min(value = 1, message = "座位图列数必须大于 0")
    private Integer cols;

    @Size(max = 64)
    private String screenLabel;

    /** 仅包含有座格子；空行/过道不传 */
    @Valid
    @NotEmpty(message = "座位不能为空")
    private List<SeatMapSeatDTO> seats;
}
