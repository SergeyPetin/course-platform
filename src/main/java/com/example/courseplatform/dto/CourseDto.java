package com.example.courseplatform.dto;

import java.math.BigDecimal;

public record CourseDto(
        Long id,
        String title,
        String description,
        BigDecimal price,
        String coverImageUrl,
        String previewVideoUrl,
        Long authorId,
        String authorFullName,
        String authorEmail
) {}