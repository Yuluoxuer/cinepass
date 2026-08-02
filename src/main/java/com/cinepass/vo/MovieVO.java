package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MovieVO {
    private String movieId;
    private String title;
    private String posterUrl;
    private List<String> genres;
    private BigDecimal rating;
    private Integer durationMin;
    private String releaseDate;
    private String status;
    private String description;
    private String cast;
    private Integer wantSeeCount;
}
