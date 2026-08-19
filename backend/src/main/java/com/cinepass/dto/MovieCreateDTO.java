package com.cinepass.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 新建电影入参。
 */
@Data
public class MovieCreateDTO {

    @NotBlank
    @Size(min = 1, max = 128)
    private String title;

    @NotBlank
    @Size(min = 1, max = 512)
    private String posterUrl;

    @NotEmpty
    private List<String> genres;

    private BigDecimal rating;

    @Min(1)
    private int durationMin;

    /** yyyy-MM-dd */
    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式须为 YYYY-MM-DD")
    private String releaseDate;

    /** hot_showing / coming_soon / off；空则服务端默认 coming_soon */
    @Pattern(regexp = "hot_showing|coming_soon|off")
    private String status;

    @NotBlank
    private String description;

    private String cast;
}
