package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class HallUpdateDTO {
    @NotBlank(message = "影厅名称不能为空")
    @Size(max = 128)
    private String name;
}
