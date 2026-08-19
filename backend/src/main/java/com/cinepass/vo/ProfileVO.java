package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 个人资料出参：观影偏好 + 想看电影 ID 列表。
 */
@Data
@Builder
public class ProfileVO {

    /** 偏好类型列表 */
    private List<String> preferGenres;

    /** 偏好排位：front / middle / back */
    private String preferRow;

    /** 偏好侧向：center / aisle / edge */
    private String preferSide;

    /** 想看电影 ID 列表 */
    private List<String> wantSeeMovieIds;
}
