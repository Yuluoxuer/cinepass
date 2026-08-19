package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.LoginDTO;
import com.cinepass.dto.PasswordChangeDTO;
import com.cinepass.dto.RegisterDTO;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.model.UserProfile;
import com.cinepass.security.JwtUtil;
import com.cinepass.security.Roles;
import com.cinepass.service.AuthService;
import com.cinepass.service.AuthSessionService;
import com.cinepass.util.PhoneMask;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.util.UserIds;
import com.cinepass.vo.AuthMeVO;
import com.cinepass.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@link AuthService} 实现。
 * <p>登录签发 Access + Refresh；注册固定 role=user；改密支持已登录或匿名带 account；登出拉黑 jti。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;

    public AuthServiceImpl(UserAccountMapper userAccountMapper,
                           UserProfileMapper userProfileMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           AuthSessionService authSessionService) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
    }

    /** 账号（昵称或手机号）+ 密码登录，校验启用状态后签发 Token */
    @Override
    public LoginVO login(LoginDTO dto) {
        String account = dto.getAccount().trim();
        UserAccount user = findByAccount(account);
        // 账号不存在与密码错误统一提示，避免据此枚举账号是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号已禁用");
        }
        return issueLogin(user);
    }

    /** 注册普通用户（角色固定 user），写空偏好档案后直接登录 */
    @Override
    @Transactional
    public LoginVO register(RegisterDTO dto) {
        if (userAccountMapper.findByNickname(dto.getNickname()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "昵称已存在");
        }
        if (userAccountMapper.findByPhone(dto.getPhone()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已注册");
        }
        OffsetDateTime now = DateTimeFormats.now();
        UserAccount user = new UserAccount();
        user.setUserId(UserIds.next());
        user.setNickname(dto.getNickname().trim());
        user.setPhone(dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        // 注册入口固定普通用户角色，后台账号走 AdminUser
        user.setRole(Roles.USER);
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        insertEmptyProfile(user.getUserId(), now);
        return issueLogin(user);
    }

    /** 旧密码校验后改密；已登录用 userId，未登录须传 account */
    @Override
    @Transactional
    public void changePassword(String currentUserId, PasswordChangeDTO dto) {
        UserAccount user;
        // 已登录优先用 SecurityContext；未登录须传 account（公开改密入口）
        if (StringUtils.hasText(currentUserId)) {
            user = userAccountMapper.findById(currentUserId);
        } else if (StringUtils.hasText(dto.getAccount())) {
            user = findByAccount(dto.getAccount().trim());
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请填写账号");
        }
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PASSWORD_WRONG);
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_SAME_AS_OLD);
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(DateTimeFormats.now());
        userAccountMapper.update(user);
    }

    /** 登出：删除 Refresh 会话，并将当前 Access jti 拉黑至过期 */
    @Override
    public void logout(String sid, String jti) {
        authSessionService.deleteRefresh(sid);
        // Access 在过期前仍可用，需把 jti 拉黑覆盖剩余 TTL
        authSessionService.denyJti(jti, jwtUtil.getExpiresInSeconds());
    }

    /** 查询当前用户资料（手机号脱敏，含角色与 cinemaId） */
    @Override
    public AuthMeVO me(String userId) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return AuthMeVO.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .phone(PhoneMask.mask(user.getPhone()))
                .role(user.getRole())
                .cinemaId(user.getCinemaId())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /** 按账号形态路由：11 位手机号查 phone，否则查昵称 */
    private UserAccount findByAccount(String account) {
        return PhoneMask.isMobile(account)
                ? userAccountMapper.findByPhone(account)
                : userAccountMapper.findByNickname(account);
    }

    /** 签发 Access Token + 持久化 Refresh 会话，组装 LoginVO */
    private LoginVO issueLogin(UserAccount user) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        String token = jwtUtil.generateAccessToken(
                user.getUserId(), user.getNickname(), user.getRole(), sid, user.getCinemaId());
        authSessionService.saveRefresh(sid, user.getUserId(), user.getRole());
        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn((int) jwtUtil.getExpiresInSeconds())
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .phone(PhoneMask.mask(user.getPhone()))
                .role(user.getRole())
                .cinemaId(user.getCinemaId())
                .build();
    }

    /** 注册时写入空偏好档案（prefer_genres=[]） */
    private void insertEmptyProfile(String userId, OffsetDateTime now) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setPreferGenresJson("[]");
        profile.setUpdatedAt(now);
        userProfileMapper.upsert(profile);
    }
}
