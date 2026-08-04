package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 创建购票 Draft 入参。
 */
@Data
public class CreateBookingDraftDTO {

    /** manual / agent / hybrid；默认 manual */
    @Pattern(regexp = "manual|agent|hybrid")
    private String source;

    /** 预填影片；有则 state 可到 SelectCinema */
    @Size(max = 32)
    private String movieId;
}
