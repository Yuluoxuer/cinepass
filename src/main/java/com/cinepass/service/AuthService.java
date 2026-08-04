package com.cinepass.service;

import com.cinepass.dto.LoginDTO;
import com.cinepass.dto.PasswordChangeDTO;
import com.cinepass.dto.RegisterDTO;
import com.cinepass.vo.AuthMeVO;
import com.cinepass.vo.LoginVO;

/**
 * 认证业务：登录、注册、改密、登出、当前用户信息；登录时把角色写入 JWT / Refresh 会话。
 */
public interface AuthService {

    /** 账号（昵称或手机号）+ 密码登录，返回带角色的 Access Token */
    LoginVO login(LoginDTO dto);

    /** 注册普通用户（固定角色 user），成功后直接登录 */
    LoginVO register(RegisterDTO dto);

    /** 旧密码 + 新密码改密。已登录用当前用户；未登录须传 account */
    void changePassword(String currentUserId, PasswordChangeDTO dto);

    /** 登出：删除 Refresh 会话，并将当前 Access jti 加入黑名单 */
    void logout(String sid, String jti);

    /** 查询当前登录用户资料（含角色，手机号脱敏） */
    AuthMeVO me(String userId);
}
