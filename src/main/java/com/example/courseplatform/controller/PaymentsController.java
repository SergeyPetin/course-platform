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
            // 🔥 МИНИМАЛЬНО! Только проверка + статическая ссылка
            String email = auth.getName();
            Long courseId = Long.valueOf(request.get("courseId").toString());

            log.info("Payment requested: user={}, course={}", email, courseId);

            // 🔥 ТЕСТОВАЯ ФОРМА ЮKassa (работает!)
            String testPaymentUrl = "https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing";

            return ResponseEntity.ok(Map.of("url", testPaymentUrl));

        } catch (Exception e) {
            log.error("Payment error", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Payment failed"));
        }
    }
}