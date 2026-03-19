package com.example.courseplatform.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long courseId;
    private String returnUrl;
}
