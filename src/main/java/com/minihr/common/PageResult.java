package com.minihr.common;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应体（字段对齐系分文档: {total, page, pageSize, items}）
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 总记录数 */
    private Long total;

    /** 数据列表 */
    private List<T> items;

    /** 当前页码 */
    private Integer page;

    /** 每页大小 */
    private Integer pageSize;

    /**
     * 构建分页结果（page / pageSize 为 null 时使用默认值）
     */
    public static <T> PageResult<T> of(Long total, List<T> items, Integer page, Integer pageSize) {
        return new PageResult<>(total, items,
                page != null ? page : DEFAULT_PAGE_NUM,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE);
    }

    /**
     * 构建分页结果（使用默认分页参数）
     */
    public static <T> PageResult<T> of(Long total, List<T> items) {
        return of(total, items, DEFAULT_PAGE_NUM, DEFAULT_PAGE_SIZE);
    }

    /**
     * 空分页结果（page / pageSize 为 null 时使用默认值）
     */
    public static <T> PageResult<T> empty(Integer page, Integer pageSize) {
        return new PageResult<>(0L, Collections.emptyList(),
                page != null ? page : DEFAULT_PAGE_NUM,
                pageSize != null ? pageSize : DEFAULT_PAGE_SIZE);
    }

    /**
     * 空分页结果（使用默认分页参数）
     */
    public static <T> PageResult<T> empty() {
        return empty(DEFAULT_PAGE_NUM, DEFAULT_PAGE_SIZE);
    }

}
