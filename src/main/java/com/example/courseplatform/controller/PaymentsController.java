package com.example.courseplatform.controller;

import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    public ResponseEntity<?> createPayment(@RequestBody Map<String, Object> request, Authentication auth) {
        try {
            log.info("🚀 1. START: user={}, courseId={}", auth.getName(), request.get("courseId"));

            // JWT Check
            if (auth == null || auth.getName() == null) {
                return ResponseEntity.status(401).body(Map.of("error", "No authentication"));
            }

            String email = auth.getName();
            Long courseId = Long.valueOf(request.get("courseId").toString());

            log.info("🚀 2. Looking for user: {}", email);
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                log.warn("🚫 User not found: {}", email);
                return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
            }

            log.info("🚀 3. Looking for course: {}", courseId);
            Course course = courseRepository.findById(courseId).orElse(null);
            if (course == null) {
                log.warn("🚫 Course not found: {}", courseId);
                return ResponseEntity.badRequest().body(Map.of("error", "Course not found"));
            }

            if (course.getPrice() == null) {
                log.warn("🚫 Course price NULL: {}", courseId);
                return ResponseEntity.badRequest().body(Map.of("error", "Course price not set"));
            }

            log.info("🚀 4. Creating Yookassa payment. Price: {}", course.getPrice());
            String paymentUrl = createYookassaPayment(courseId, course.getPrice(), user.getId());
            log.info("✅ 5. SUCCESS! URL: {}", paymentUrl);

            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (Exception e) {
            log.error("💥 ERROR: {} | {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    private String createYookassaPayment(Long courseId, BigDecimal amount, Long userId) throws Exception {
        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // Безопасный JSON
        String json = """
                {
                    "amount": {"value": "%s", "currency": "RUB"},
                    "confirmation": {"type": "redirect", "return_url": "https://front-production-c924.up.railway.app/courses/%d"},
                    "capture": true,
                    "description": "Course #%d"
                }
                """.formatted(amount.toString(), courseId, courseId);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.yookassa.ru/v3/payments"))
                .header("Authorization", "Basic " + base64Auth)
                .header("Idempotence-Key", idempotenceKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Yookassa response: {} {}", response.statusCode(), response.body());

        if (response.statusCode() == 201) {
            JsonNode node = objectMapper.readTree(response.body());
            return node.path("confirmation").path("confirmation_url").asText();
        }

        throw new RuntimeException("Yookassa failed: " + response.statusCode());
    }
}