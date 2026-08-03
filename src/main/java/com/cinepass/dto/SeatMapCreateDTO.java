package com.cinepass.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class SeatMapCreateDTO {
    @Size(max = 32)
    private String seatMapId;

    private String cinemaId;

    @Min(1)
    private Integer rows;

    @Min(1)
    private Integer cols;

    @Size(max = 64)
    private String screenLabel;

    @Valid
    @NotEmpty(message = "座位不能为空")
    private List<SeatMapSeatDTO> seats;
}
