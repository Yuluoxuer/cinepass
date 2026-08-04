package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class HallCreateDTO {
    @Size(max = 32)
    private String hallId;

    private String cinemaId;

    @NotBlank(message = "影厅名称不能为空")
    @Size(max = 128)
    private String name;

    @NotBlank(message = "座位图不能为空")
    @Size(max = 32)
    private String seatMapId;
}
