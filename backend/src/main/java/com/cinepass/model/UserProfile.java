package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 用户偏好档案表 {@code user_profile} 映射。
 */
@Data
public class UserProfile implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户 ID，与 user_account 一对一 */
    private String userId;

    /** 偏好类型 JSON 数组字符串，如 {@code ["喜剧"]}；MyBatis 以 String 读写 JSONB */
    private String preferGenresJson;

    /** 偏好排位：front / middle / back */
    private String preferRow;

    /** 偏好侧向：center / aisle / edge */
    private String preferSide;

    /** 更新时间 */
    private OffsetDateTime updatedAt;
}
