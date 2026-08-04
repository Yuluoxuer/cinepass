package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class Movie implements Serializable {
    private static final long serialVersionUID = 1L;
    private String movieId;
    private String title;
    private String posterUrl;
    private String genresJson;
    private BigDecimal rating;
    private Integer durationMin;
    private LocalDate releaseDate;
    private String status;
    private String description;
    private String castText;
    private Integer wantSeeCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
