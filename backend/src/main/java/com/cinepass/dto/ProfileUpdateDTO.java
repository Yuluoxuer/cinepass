package com.cinepass.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新个人观影偏好入参。
 */
@Data
public class ProfileUpdateDTO {

    /** 偏好类型列表，如 ["喜剧","科幻"] */
    private List<String> preferGenres;

    /** 偏好排位：front / middle / back */
    private String preferRow;

    /** 偏好侧向：center / aisle / edge */
    private String preferSide;
}
