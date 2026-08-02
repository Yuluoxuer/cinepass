package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    /** JSON 数组字符串，如 ["喜剧"]；MyBatis 以 String 读写 JSONB */
    private String preferGenresJson;
    private String preferRow;
    private String preferSide;
    private OffsetDateTime updatedAt;
}
