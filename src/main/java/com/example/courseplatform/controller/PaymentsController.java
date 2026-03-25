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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentsController {

    @Value("${yookassa.shop-id}")
    private String shopId;

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

            String paymentUrl = createSimplePayUrl(courseId, course.getPrice(), user.getId(), course.getTitle());

            log.info("Yookassa SimplePay created: {}", paymentUrl);
            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (IllegalArgumentException e) {
            log.warn("Payment validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Yookassa payment failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Yookassa service unavailable"));
        }
    }

    private String createSimplePayUrl(Long courseId, BigDecimal price, Long userId, String title) {
        try {
            String label = userId + "_" + courseId;
            String amount = price.toString();  // BigDecimal → String
            String description = URLEncoder.encode("Course: " + title, StandardCharsets.UTF_8);
            String successUrl = URLEncoder.encode("https://front-production-c924.up.railway.app/courses/" + courseId, StandardCharsets.UTF_8);

            // 🔥 ЮKassa SimplePay Форма оплаты
            return "https://yookassa.ru/simplepay/confirm.xml?" +
                    "shopId=" + shopId +
                    "&scid=course_" + courseId +
                    "&sum=" + amount +
                    "&targets=" + description +
                    "&paymentType=PC" +
                    "&label=" + label +
                    "&successURL=" + successUrl;
        } catch (Exception e) {
            log.error("SimplePay URL failed", e);
            return "https://yookassa.ru/simplepay/confirm.xml?shopId=" + shopId;
        }
    }
}