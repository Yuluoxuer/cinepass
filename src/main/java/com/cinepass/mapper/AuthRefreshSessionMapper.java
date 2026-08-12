package com.cinepass.mapper;

import com.cinepass.model.AuthRefreshSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * Refresh 会话表 Mapper；登录写入、静默续期读取、登出删除。
 */
@Mapper
public interface AuthRefreshSessionMapper {

    /** 按 sid 幂等写入或覆盖（重新登录同 sid 极少见，兼容 upsert） */
    int upsert(AuthRefreshSession row);

    /** 按 sid 查未过期会话；不存在或已过期返回 null */
    AuthRefreshSession findValidBySid(@Param("sid") String sid, @Param("now") OffsetDateTime now);

    /** 按 sid 删除会话（登出） */
    int deleteBySid(@Param("sid") String sid);
}
