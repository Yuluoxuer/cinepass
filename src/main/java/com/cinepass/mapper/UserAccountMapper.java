package com.cinepass.mapper;

import com.cinepass.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAccountMapper {
    UserAccount findById(@Param("userId") String userId);
    UserAccount findByNickname(@Param("nickname") String nickname);
    UserAccount findByPhone(@Param("phone") String phone);
    int insert(UserAccount row);
    int update(UserAccount row);
    long countActiveAdmins(@Param("excludeUserId") String excludeUserId);
    long countStaffByCinemaId(@Param("cinemaId") String cinemaId);
    List<UserAccount> page(@Param("role") String role, @Param("status") Integer status,
                           @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("role") String role, @Param("status") Integer status);
}
