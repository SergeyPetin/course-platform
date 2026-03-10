package com.example.courseplatform.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String email;
    private String role;
}
