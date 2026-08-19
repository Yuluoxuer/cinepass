package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 想看操作结果：目标影片及当前是否已想看。
 */
@Data
@Builder
public class WantSeeVO {

    /** 电影 ID */
    private String movieId;

    /** true=已加入想看，false=已取消 */
    private boolean wanted;
}
