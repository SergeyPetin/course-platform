package com.example.courseplatform.dto;

public record LessonDto(
        Long id,
        String title,
        String videoId,
        Integer orderNumber,
        int durationMinutes
) {}