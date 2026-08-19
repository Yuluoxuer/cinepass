package com.cinepass.service.impl;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.AdminUserCreateDTO;
import com.cinepass.dto.AdminUserUpdateDTO;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.model.UserProfile;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.AdminUserService;
import com.cinepass.util.PhoneMask;
import com.cinepass.util.DateTimeFormats;
import com.cinepass.util.UserIds;
import com.cinepass.vo.AdminUserVO;
import com.cinepass.vo.PageResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AdminUserService} 实现。
 * <p>约束：staff 必须绑影院；禁止自降权/自禁用；至少保留一名启用管理员；出参手机号脱敏。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(UserAccountMapper userAccountMapper,
                                UserProfileMapper userProfileMapper,
                                PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** 按角色、状态分页查询用户；page/size 越界回落，size 上限 50 */
    @Override
    public PageResult<AdminUserVO> page(String role, Integer status, int page, int size) {
        // size 上限 50，与订单等列表接口一致，防止一次拉过大页
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 50) size = 50;
        long total = userAccountMapper.count(role, status);
        List<UserAccount> rows = userAccountMapper.page(role, status, (page - 1) * size, size);
        List<AdminUserVO> items = new ArrayList<AdminUserVO>();
        if (rows != null) {
            for (UserAccount u : rows) {
                items.add(toVo(u));
            }
        }
        return new PageResult<AdminUserVO>(items, page, size, total);
    }

    /** 创建账号（含角色/影院绑定）并初始化空偏好档案 */
    @Override
    @Transactional
    public AdminUserVO create(AdminUserCreateDTO dto) {
        if (userAccountMapper.findByNickname(dto.getNickname()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "昵称已存在");
        }
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : null;
        if (phone != null && userAccountMapper.findByPhone(phone) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已存在");
        }
        String cinemaId = resolveCinemaIdForRole(dto.getRole(), dto.getCinemaId());
        OffsetDateTime now = DateTimeFormats.now();
        UserAccount user = new UserAccount();
        user.setUserId(UserIds.next());
        user.setNickname(dto.getNickname().trim());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        user.setCinemaId(cinemaId);
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);

        // 同步空偏好档案，避免 /me/profile 首次读取缺行
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getUserId());
        profile.setPreferGenresJson("[]");
        profile.setUpdatedAt(now);
        userProfileMapper.upsert(profile);
        return toVo(user);
    }

    /** 部分更新用户；含自降权保护与末位管理员保护 */
    @Override
    @Transactional
    public AdminUserVO update(String userId, AdminUserUpdateDTO dto) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String currentUserId = SecurityContext.getCurrentUserId();
        String newRole = dto.getRole() != null ? dto.getRole() : user.getRole();
        Integer newStatus = dto.getStatus() != null ? dto.getStatus() : user.getStatus();

        // 禁止操作者把自己降权或禁用，避免锁死后台
        if (userId.equals(currentUserId)) {
            if (!Roles.ADMIN.equals(newRole) || (newStatus != null && newStatus == 0)) {
                throw new BusinessException(ResultCode.FAIL, "禁止将当前管理员降权或禁用");
            }
        }

        boolean wasActiveAdmin = Roles.ADMIN.equals(user.getRole()) && user.getStatus() != null && user.getStatus() == 1;
        boolean willBeActiveAdmin = Roles.ADMIN.equals(newRole) && newStatus != null && newStatus == 1;
        // 末位启用管理员不可被降权/禁用，否则系统无人可管
        if (wasActiveAdmin && !willBeActiveAdmin) {
            long others = userAccountMapper.countActiveAdmins(userId);
            if (others < 1) {
                throw new BusinessException(ResultCode.FAIL, "系统至少保留一名启用中的管理员");
            }
        }

        if (dto.getNickname() != null) {
            UserAccount byNick = userAccountMapper.findByNickname(dto.getNickname());
            if (byNick != null && !byNick.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.CONFLICT, "昵称已存在");
            }
            user.setNickname(dto.getNickname());
        }
        if (dto.getPhone() != null) {
            // 空串表示清空手机号
            String phone = dto.getPhone().isEmpty() ? null : dto.getPhone();
            if (phone != null) {
                UserAccount byPhone = userAccountMapper.findByPhone(phone);
                if (byPhone != null && !byPhone.getUserId().equals(userId)) {
                    throw new BusinessException(ResultCode.CONFLICT, "手机号已存在");
                }
            }
            user.setPhone(phone);
        }
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        // 角色或 cinemaId 任一变更时，按最终 role 校验并写回 cinema_id
        boolean roleChanged = dto.getRole() != null;
        boolean cinemaTouched = dto.getCinemaId() != null;
        if (roleChanged || cinemaTouched) {
            String candidateCinema;
            if (!Roles.STAFF.equals(newRole) && !cinemaTouched) {
                // 降级/改为非 staff 且未传 cinemaId：自动清空，避免沿用旧 staff 影院触发 400
                candidateCinema = null;
            } else if (cinemaTouched) {
                candidateCinema = dto.getCinemaId().isEmpty() ? null : dto.getCinemaId();
            } else {
                candidateCinema = user.getCinemaId();
            }
            String resolved = resolveCinemaIdForRole(newRole, candidateCinema);
            user.setCinemaId(resolved);
            user.setUpdateCinemaId(Boolean.TRUE);
        }

        user.setUpdatedAt(DateTimeFormats.now());
        userAccountMapper.update(user);
        return toVo(userAccountMapper.findById(userId));
    }

    /**
     * staff 必须有 cinemaId；user/admin 禁止绑定影院。
     */
    private String resolveCinemaIdForRole(String role, String cinemaId) {
        if (Roles.STAFF.equals(role)) {
            if (!StringUtils.hasText(cinemaId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "工作人员必须绑定影院 cinemaId");
            }
            return cinemaId.trim();
        }
        if (StringUtils.hasText(cinemaId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅工作人员可绑定影院");
        }
        return null;
    }

    /** 转为后台 VO；手机号脱敏 */
    private AdminUserVO toVo(UserAccount u) {
        return AdminUserVO.builder()
                .userId(u.getUserId())
                .nickname(u.getNickname())
                .phone(PhoneMask.mask(u.getPhone()))
                .role(u.getRole())
                .cinemaId(u.getCinemaId())
                .status(u.getStatus() == null ? 0 : u.getStatus())
                .createdAt(u.getCreatedAt() != null ? DateTimeFormats.format(u.getCreatedAt()) : null)
                .build();
    }
}
