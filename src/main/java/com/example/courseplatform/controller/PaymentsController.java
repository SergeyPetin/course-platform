package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    private final ObjectMapper objectMapper;

    @PostMapping("/create")
    public ResponseEntity<?> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth
    ) {
        try {
            log.info("Payment request: courseId={}, user={}", request.get("courseId"), auth.getName());

            // Безопасная проверка JWT
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
            }

            String email = auth.getName();
            Long courseId = Long.valueOf(request.get("courseId").toString());

            log.info("Finding user: {}, course: {}", email, courseId);

            // Безопасный поиск
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.warn("User not found: {}", email);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) {
                log.warn("Course not found: {}", courseId);
                return ResponseEntity.badRequest().body(Map.of("error", "Course not found"));
            }

            String paymentUrl = createYookassaPayment(courseId, course.getPrice(), user.getId());
            log.info("✅ SUCCESS URL: {}", paymentUrl);

            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (Exception e) {
            log.error("Payment FAILED: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of("error", "Payment service error: " + e.getMessage()));
        }
    }

    private String createYookassaPayment(Long courseId, BigDecimal amount, Long userId) throws Exception {
        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        String json = String.format("""
            {
                "amount": {"value": "%s", "currency": "RUB"},
                "confirmation": {"type": "redirect", "return_url": "https://front-production-c924.up.railway.app/courses/%d"},
                "capture": true,
                "description": "Course #%d"
            }
            """, amount.toString(), courseId, courseId);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.yookassa.ru/v3/payments"))
                .header("Authorization", "Basic " + base64Auth)
                .header("Idempotence-Key", idempotenceKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode node = objectMapper.readTree(response.body());
            String confirmationUrl = node.path("confirmation").path("confirmation_url").asText();
            log.info("Yookassa payment created successfully: {}", confirmationUrl);
            return confirmationUrl;
        }

        log.error("Yookassa API error {}: {}", response.statusCode(), response.body());
        throw new RuntimeException("Yookassa API error: " + response.statusCode());
    }
}