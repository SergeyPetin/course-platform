package com.example.courseplatform.dto;

import java.time.LocalDateTime;

public record SubscriptionDto(
        Long id,
        Long courseId,
        String status,
        LocalDateTime purchaseDate,
        LocalDateTime expiresAt
) {}