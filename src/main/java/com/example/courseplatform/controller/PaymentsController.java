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

            String paymentUrl = createQuickPayUrl(courseId, course.getPrice(), user.getId(), course.getTitle());

            log.info("QuickPay URL created: {}", paymentUrl);
            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (IllegalArgumentException e) {
            log.warn("Payment validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Payment creation failed", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Payment service unavailable"));
        }
    }

    private String createQuickPayUrl(Long courseId, BigDecimal price, Long userId, String title) {
        try {
            String label = userId + "_" + courseId;
            String amount = price.toString();
            String target = URLEncoder.encode("Курс: " + title, StandardCharsets.UTF_8);

            // 🔥 ЮMoney QuickPay — работает НЕМЕДЛЕННО!
            return String.format(
                    "https://yoomoney.ru/quickpay/confirm.xml?" +
                            "receiver=410011644936395&" +
                            "quickpay-form=shop&" +
                            "targets=%s&" +
                            "paymentType=PC&" +
                            "sum=%s&" +
                            "label=%s",
                    target, amount, label
            );
        } catch (Exception e) {
            log.error("QuickPay URL failed", e);
            return "https://yoomoney.ru/quickpay/confirm.xml?receiver=410011644936395";
        }
    }
}