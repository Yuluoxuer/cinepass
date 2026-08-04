package com.cinepass.service;

import com.cinepass.dto.ProfileUpdateDTO;
import com.cinepass.vo.ProfileVO;

/**
 * 个人资料：查询 / 更新观影偏好，并附带想看列表。
 */
public interface ProfileService {

    /** 查询用户偏好与全部想看电影 ID */
    ProfileVO get(String userId);

    /** 校验并 upsert 观影偏好，返回更新后的资料 */
    ProfileVO update(String userId, ProfileUpdateDTO dto);
}
