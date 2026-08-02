package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class WantSee implements Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String movieId;
    private OffsetDateTime createdAt;
}
