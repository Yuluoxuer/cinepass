package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 个人推荐响应（系分 §4.2）。
 */
@Data
@Builder
public class PersonalRecoVO {

    /** 推荐模式：personalized / fallback_hot */
    private String mode;

    /** 推荐项，按 personalScore 降序 */
    private List<PersonalRecoItemVO> items;
}
