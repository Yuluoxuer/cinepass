package com.cinepass.service;

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
import com.cinepass.util.PhoneMask;
import com.cinepass.util.UserIds;
import com.cinepass.vo.AuthMeVO;
import com.cinepass.vo.LoginVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;

    public AuthService(UserAccountMapper userAccountMapper,
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

    public LoginVO login(LoginDTO dto) {
        String account = dto.getAccount().trim();
        UserAccount user = findByAccount(account);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "账号不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号已禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "密码错误");
        }
        return issueLogin(user);
    }

    @Transactional
    public LoginVO register(RegisterDTO dto) {
        if (userAccountMapper.findByNickname(dto.getNickname()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "昵称已存在");
        }
        if (userAccountMapper.findByPhone(dto.getPhone()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已注册");
        }
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setUserId(UserIds.next());
        user.setNickname(dto.getNickname().trim());
        user.setPhone(dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Roles.USER);
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        insertEmptyProfile(user.getUserId(), now);
        return issueLogin(user);
    }

    /**
     * 旧密码 + 新密码改密。已登录用当前用户；未登录须传 account。
     */
    @Transactional
    public void changePassword(String currentUserId, PasswordChangeDTO dto) {
        UserAccount user;
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
            throw new BusinessException(ResultCode.UNAUTHORIZED, "旧密码不正确");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与旧密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        userAccountMapper.update(user);
    }

    public void logout(String sid, String jti) {
        authSessionService.deleteRefresh(sid);
        authSessionService.denyJti(jti, jwtUtil.getExpiresInSeconds());
    }

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
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    private UserAccount findByAccount(String account) {
        return PhoneMask.isMobile(account)
                ? userAccountMapper.findByPhone(account)
                : userAccountMapper.findByNickname(account);
    }

    private LoginVO issueLogin(UserAccount user) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        String token = jwtUtil.generateAccessToken(user.getUserId(), user.getNickname(), user.getRole(), sid);
        authSessionService.saveRefresh(sid, user.getUserId(), user.getRole());
        return LoginVO.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn((int) jwtUtil.getExpiresInSeconds())
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .phone(PhoneMask.mask(user.getPhone()))
                .role(user.getRole())
                .build();
    }

    private void insertEmptyProfile(String userId, OffsetDateTime now) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setPreferGenresJson("[]");
        profile.setUpdatedAt(now);
        userProfileMapper.upsert(profile);
    }
}
