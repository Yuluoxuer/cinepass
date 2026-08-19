package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 场次列表响应（按日期分组外壳）。
 */
@Data
@Builder
public class ShowListResult {

    /** 查询日期 yyyy-MM-dd；全量列表时可为 null */
    private String date;

    private List<ShowVO> items;
}
