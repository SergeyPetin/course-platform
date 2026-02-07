package com.example.courseplatform.dto;

import com.example.courseplatform.model.User;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateCourseDto {

    private String title;
    private String description;
    @DecimalMin(value = "0.0", inclusive = true, message = "Цена должна быть 0 или больше")
    private BigDecimal price;
    private User author;

}
