package com.cinepass.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 影片类型标签字典 Mapper。
 */
@Mapper
public interface TagMapper {

    /** 幂等插入标签（name 唯一，已存在则忽略） */
    int insertIgnore(@Param("tagId") String tagId,
                     @Param("name") String name,
                     @Param("createdAt") OffsetDateTime createdAt);

    /** 全部标签名，按名称排序 */
    List<String> listAllNames();
}
