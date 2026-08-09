package com.cinepass.cache;

/**
// * 影片变更事件：建片/改片/状态翻转后发布，用于失效推荐底座缓存。
 */
public class MovieChangedEvent {

    private final String movieId;

    public MovieChangedEvent(String movieId) {
        this.movieId = movieId;
    }

    public String getMovieId() {
        return movieId;
    }
}
