package com.cinepass.mapper;

import com.cinepass.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户偏好档案 Mapper。
 */
@Mapper
public interface UserProfileMapper {

    /** 按用户 ID 查询偏好档案 */
    UserProfile findById(@Param("userId") String userId);

    /** 插入或更新偏好档案 */
    int upsert(UserProfile row);
}
