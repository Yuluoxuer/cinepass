package com.cinepass.mapper;

import com.cinepass.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户账号表 Mapper；后台用户/角色管理依赖分页与管理员计数等方法。
 */
@Mapper
public interface UserAccountMapper {

    /** 按用户 ID 查询账号 */
    UserAccount findById(@Param("userId") String userId);

    /** 按昵称查询账号（唯一性校验） */
    UserAccount findByNickname(@Param("nickname") String nickname);

    /** 按手机号查询账号（唯一性校验） */
    UserAccount findByPhone(@Param("phone") String phone);

    /** 插入新账号 */
    int insert(UserAccount row);

    /** 更新账号（含角色、状态、密码等） */
    int update(UserAccount row);

    /** 统计启用中的管理员数量（可排除指定用户，用于末位管理员保护） */
    long countActiveAdmins(@Param("excludeUserId") String excludeUserId);

    /** 按角色、状态分页查询账号列表 */
    List<UserAccount> page(@Param("role") String role, @Param("status") Integer status,
                           @Param("offset") int offset, @Param("limit") int limit);

    /** 按角色、状态统计账号总数 */
    long count(@Param("role") String role, @Param("status") Integer status);
}
