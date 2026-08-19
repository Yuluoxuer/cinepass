package com.cinepass.mapper;

import com.cinepass.model.RecoWeight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * 热门公式权重表 {@code reco_weight} Mapper。
 */
@Mapper
public interface RecoWeightMapper {

    /** 按城市查权重；无配置返回 null */
    RecoWeight selectByCity(@Param("cityId") String cityId);

    /** 城市首次出现时插入默认权重；已存在则忽略（幂等 seed） */
    int insertDefault(@Param("cityId") String cityId, @Param("updatedAt") OffsetDateTime updatedAt);
}
