package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProfileVO {
    private List<String> preferGenres;
    private String preferRow;
    private String preferSide;
    private List<String> wantSeeMovieIds;
}
