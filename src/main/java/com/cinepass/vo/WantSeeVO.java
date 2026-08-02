package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WantSeeVO {
    private String movieId;
    private boolean wanted;
}
