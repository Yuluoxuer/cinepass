package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 想看关联表 {@code want_see} 映射（用户–影片）。
 */
@Data
public class WantSee implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private String userId;

    /** 电影 ID */
    private String movieId;

    /** 加入想看时间 */
    private OffsetDateTime createdAt;
}
