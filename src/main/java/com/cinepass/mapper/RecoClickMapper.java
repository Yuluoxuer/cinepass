package com.cinepass.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 影片点击日计数表 {@code reco_clicks} Mapper。
 */
@Mapper
public interface RecoClickMapper {

    /** 当日点击 +1；跨天自动新行（幂等 UPSERT） */
    int incrClick(@Param("movieId") String movieId, @Param("clickDate") LocalDate clickDate);
}
