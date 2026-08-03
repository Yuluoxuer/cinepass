package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果（业务 VO 包）。
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> items;

    /** 当前页码（从 1 起） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 总记录数 */
    private long total;
}
