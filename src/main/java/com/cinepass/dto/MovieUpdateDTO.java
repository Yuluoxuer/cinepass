package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 更新电影入参（部分字段；null 表示不改）。
 */
@Data
public class MovieUpdateDTO {

    @Size(min = 1, max = 128)
    private String title;

    @Size(min = 1, max = 512)
    private String posterUrl;

    private List<String> genres;

    private BigDecimal rating;

    @Min(1)
    private Integer durationMin;

    /** yyyy-MM-dd */
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式须为 YYYY-MM-DD")
    private String releaseDate;

    /** hot_showing / coming_soon / off */
    @Pattern(regexp = "hot_showing|coming_soon|off")
    private String status;

    private String description;

    private String cast;
}
