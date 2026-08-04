package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class SeatStatus implements Serializable {
    private static final long serialVersionUID = 1L;
    private String showId;
    private String seatId;
    private String status;
    private String lockId;
    private String userId;
    private OffsetDateTime expireAt;
    private OffsetDateTime updatedAt;
}
