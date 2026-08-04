package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 新建影厅入参。
 */
@Data
public class HallCreateDTO {

    /** 可选；空则服务端生成 {@code h + UUID7} */
    @Size(max = 32)
    private String hallId;

    /** 所属影院；admin 必填，staff 可省略（取绑定影院） */
    private String cinemaId;

    @NotBlank(message = "影厅名称不能为空")
    @Size(max = 128)
    private String name;

    /** 须属于同一影院且已存在的座位图 */
    @NotBlank(message = "座位图不能为空")
    @Size(max = 32)
    private String seatMapId;
}
