package com.cinepass.service;

import com.cinepass.dto.AdminUserCreateDTO;
import com.cinepass.dto.AdminUserUpdateDTO;
import com.cinepass.vo.AdminUserVO;
import com.cinepass.vo.PageResult;

/**
 * 后台用户管理：分页、创建、更新；含自降权/末位管理员保护与 staff 影院绑定。
 */
public interface AdminUserService {

    /**
     * 按角色、状态分页查询用户；page/size 越界时回落默认值，size 上限 50。
     */
    PageResult<AdminUserVO> page(String role, Integer status, int page, int size);

    /**
     * 创建用户账号，并初始化空偏好档案（prefer_genres = []）。
     */
    AdminUserVO create(AdminUserCreateDTO dto);

    /**
     * 部分更新用户字段。禁止操作者自降权/自禁用；系统至少保留一名启用中的管理员。
     */
    AdminUserVO update(String userId, AdminUserUpdateDTO dto);
}
