package com.cinepass.mapper;

import com.cinepass.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileMapper {
    UserProfile findById(@Param("userId") String userId);
    int upsert(UserProfile row);
}
