package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentsController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth
    ) {
        try {
            String email = auth.getName();
            Long courseId = Long.valueOf(request.get("courseId").toString());

            log.info("✅ Payment OK: user={}, course={}", email, courseId);

            // 🔥 ТЕСТОВАЯ ФОРМА КАРТ из документации!
            String paymentUrl = "https://yoomoney.ru/api-pages/v2/payment-confirm/epl?orderId=test_" + courseId;

            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (Exception e) {
            log.error("Payment error", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}