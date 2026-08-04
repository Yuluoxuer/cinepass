package com.cinepass.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ShowListResult {
    private String date;
    private List<ShowVO> items;
}
