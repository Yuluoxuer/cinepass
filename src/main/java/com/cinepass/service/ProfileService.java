package com.cinepass.service;

import com.alibaba.fastjson2.JSON;
import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.ProfileUpdateDTO;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.mapper.WantSeeMapper;
import com.cinepass.model.UserProfile;
import com.cinepass.vo.ProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProfileService {

    private static final Set<String> ROWS = new HashSet<String>(Arrays.asList("front", "middle", "back"));
    private static final Set<String> SIDES = new HashSet<String>(Arrays.asList("center", "aisle", "edge"));

    private final UserProfileMapper userProfileMapper;
    private final WantSeeMapper wantSeeMapper;

    public ProfileService(UserProfileMapper userProfileMapper, WantSeeMapper wantSeeMapper) {
        this.userProfileMapper = userProfileMapper;
        this.wantSeeMapper = wantSeeMapper;
    }

    public ProfileVO get(String userId) {
        UserProfile profile = userProfileMapper.findById(userId);
        List<String> genres = Collections.emptyList();
        String row = null;
        String side = null;
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

    private List<String> parseGenres(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = JSON.parseArray(json, String.class);
        return list != null ? list : Collections.<String>emptyList();
    }
}
