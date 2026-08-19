package com.cinepass.service;

/**
 * Elasticsearch 索引初始化与业务数据同步服务。
 */
public interface EsIndexService {

    /** 确保索引存在，并用数据库当前数据执行一次全量同步 */
    void initializeIndicesAndSync();

    /** 同步指定影片文档 */
    void syncMovie(String movieId);

    /** 同步指定影院文档及其在售影片、最低票价 */
    void syncCinema(String cinemaId);

    /** 删除指定影院索引文档 */
    void deleteCinema(String cinemaId);
}
