package com.cinepass.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProfileUpdateDTO {
    private List<String> preferGenres;
    private String preferRow;
    private String preferSide;
}
