package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentsController {

    @Value("${yookassa.shop-id}")
    private String shopId;

    @Value("${yookassa.secret-key}")
    private String secretKey;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth
    ) {
        try {
            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            Long courseId = Long.valueOf(request.get("courseId").toString());
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("Course not found"));

            // ТЕМП: возвращаем заглушку ЮKassa
            String paymentUrl = String.format(
                    "https://yookassa.ru/my/development/test-payments?shopId=%s&course=%d",
                    shopId, courseId
            );

            log.info("Payment URL created: {} -> {}", user.getId(), paymentUrl);
            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Payment creation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Payment service unavailable"));
        }
    }
}