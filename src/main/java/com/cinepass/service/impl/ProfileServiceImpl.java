package com.cinepass.service.impl;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.ProfileUpdateDTO;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.mapper.WantSeeMapper;
import com.cinepass.model.UserProfile;
import com.cinepass.service.ProfileService;
import com.cinepass.vo.ProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ProfileService} 实现。
 * <p>偏好行/侧向白名单校验；档案缺失时按空偏好返回；更新走 upsert。
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    /** 合法 preferRow */
    private static final Set<String> ROWS = new HashSet<String>(Arrays.asList("front", "middle", "back"));
    /** 合法 preferSide */
    private static final Set<String> SIDES = new HashSet<String>(Arrays.asList("center", "aisle", "edge"));

    private final UserProfileMapper userProfileMapper;
    private final WantSeeMapper wantSeeMapper;

    public ProfileServiceImpl(UserProfileMapper userProfileMapper, WantSeeMapper wantSeeMapper) {
        this.userProfileMapper = userProfileMapper;
        this.wantSeeMapper = wantSeeMapper;
    }

    /** 查询用户偏好与全部想看电影 ID；无档案时偏好为空 */
    @Override
    public ProfileVO get(String userId) {
        UserProfile profile = userProfileMapper.findById(userId);
        List<String> genres = Collections.emptyList();
        String row = null;
        String side = null;
        // 老账号可能尚未写过 profile，按空偏好兜底
        if (profile != null) {
            genres = parseGenres(profile.getPreferGenresJson());
            row = profile.getPreferRow();
            side = profile.getPreferSide();
        }
        List<String> wantIds = wantSeeMapper.listAllMovieIds(userId);
        if (wantIds == null) {
            wantIds = Collections.emptyList();
        }
        return ProfileVO.builder()
                .preferGenres(genres)
                .preferRow(row)
                .preferSide(side)
                .wantSeeMovieIds(wantIds)
                .build();
    }

    /** 校验 preferRow/preferSide 白名单后 upsert 偏好，返回最新资料 */
    @Override
    @Transactional
    public ProfileVO update(String userId, ProfileUpdateDTO dto) {
        if (dto.getPreferRow() != null && !ROWS.contains(dto.getPreferRow())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "preferRow 非法");
        }
        if (dto.getPreferSide() != null && !SIDES.contains(dto.getPreferSide())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "preferSide 非法");
        }
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        List<String> genres = dto.getPreferGenres() != null ? dto.getPreferGenres() : Collections.<String>emptyList();
        profile.setPreferGenresJson(JSON.toJSONString(genres));
        profile.setPreferRow(dto.getPreferRow());
        profile.setPreferSide(dto.getPreferSide());
        profile.setUpdatedAt(OffsetDateTime.now());
        userProfileMapper.upsert(profile);
        return get(userId);
    }

    /** 解析 prefer_genres JSON 为列表；非法/空则返回空列表 */
    private List<String> parseGenres(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = JSON.parseArray(json, String.class);
        return list != null ? list : Collections.<String>emptyList();
    }
}
