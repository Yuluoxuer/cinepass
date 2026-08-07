package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 个人推荐单项（系分 §4.2）。
 */
@Data
@Builder
public class PersonalRecoItemVO {

    /** 影片快照 */
    private MovieVO movie;

    /** 个人推荐分 0–1，保留 4 位小数 */
    private BigDecimal personalScore;

    /** 推荐理由文案；无理由为 null */
    private String reason;
}
