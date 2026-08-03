package com.cinepass.service;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.AdminUserCreateDTO;
import com.cinepass.dto.AdminUserUpdateDTO;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.mapper.CinemaMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.model.UserProfile;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.util.PhoneMask;
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

@Service
public class AdminUserService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final CinemaMapper cinemaMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserAccountMapper userAccountMapper,
                            UserProfileMapper userProfileMapper,
                            CinemaMapper cinemaMapper,
                            PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.cinemaMapper = cinemaMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<AdminUserVO> page(String role, Integer status, int page, int size) {
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

    @Transactional
    public AdminUserVO create(AdminUserCreateDTO dto) {
        if (userAccountMapper.findByNickname(dto.getNickname()) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "昵称已存在");
        }
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone() : null;
        if (phone != null && userAccountMapper.findByPhone(phone) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "手机号已存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setUserId(UserIds.next());
        user.setNickname(dto.getNickname().trim());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setRole(dto.getRole());
        user.setCinemaId(resolveCinemaId(dto.getRole(), dto.getCinemaId(), null));
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getUserId());
        profile.setPreferGenresJson("[]");
        profile.setUpdatedAt(now);
        userProfileMapper.upsert(profile);
        return toVo(user);
    }

    @Transactional
    public AdminUserVO update(String userId, AdminUserUpdateDTO dto) {
        UserAccount user = userAccountMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String currentUserId = SecurityContext.getCurrentUserId();
        String newRole = dto.getRole() != null ? dto.getRole() : user.getRole();
        Integer newStatus = dto.getStatus() != null ? dto.getStatus() : user.getStatus();
        String cinemaId = resolveCinemaId(newRole, dto.getCinemaId(), user.getCinemaId());

        if (userId.equals(currentUserId)) {
            if (!Roles.ADMIN.equals(newRole) || (newStatus != null && newStatus == 0)) {
                throw new BusinessException(ResultCode.FAIL, "禁止将当前管理员降权或禁用");
            }
        }

        boolean wasActiveAdmin = Roles.ADMIN.equals(user.getRole()) && user.getStatus() != null && user.getStatus() == 1;
        boolean willBeActiveAdmin = Roles.ADMIN.equals(newRole) && newStatus != null && newStatus == 1;
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
        user.setCinemaId(cinemaId);
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        user.setUpdatedAt(OffsetDateTime.now());
        userAccountMapper.update(user);
        return toVo(userAccountMapper.findById(userId));
    }

    private AdminUserVO toVo(UserAccount u) {
        return AdminUserVO.builder()
                .userId(u.getUserId())
                .nickname(u.getNickname())
                .phone(PhoneMask.mask(u.getPhone()))
                .role(u.getRole())
                .cinemaId(u.getCinemaId())
                .status(u.getStatus() == null ? 0 : u.getStatus())
                .createdAt(u.getCreatedAt() != null ? FMT.format(u.getCreatedAt()) : null)
                .build();
    }

    private String resolveCinemaId(String role, String requestedCinemaId, String currentCinemaId) {
        if (!Roles.STAFF.equals(role)) {
            return null;
        }
        String cinemaId = StringUtils.hasText(requestedCinemaId) ? requestedCinemaId.trim() : currentCinemaId;
        if (!StringUtils.hasText(cinemaId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "员工必须绑定影院");
        }
        if (!cinemaMapper.exists(cinemaId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "影院不存在");
        }
        return cinemaId;
    }
}
