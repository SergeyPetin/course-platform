package com.example.courseplatform.controller;

import com.example.courseplatform.dto.PaymentRequest;
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
import java.util.HashMap;
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
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequest request,
                                           Authentication auth) {
        try {
            if (auth == null || !auth.isAuthenticated()) {
                log.warn("Unauthorized payment attempt");
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

            Long courseId = request.getCourseId();
            if (courseId == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "courseId is required"));
            }

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Курс не найден: " + courseId));

            BigDecimal amount = course.getPrice();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Некорректная цена курса: " + amount);
            }

            log.info("🚀 START payment: user={}, userId={}, courseId={}, amount={}",
                    email, user.getId(), courseId, amount);

            String returnUrl = "https://front-production-c924.up.railway.app/courses/" + courseId;

            String paymentUrl = createYookassaPayment(courseId, amount, user.getId(), returnUrl);
            log.info("✅ SUCCESS URL: {}", paymentUrl);

            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (Exception e) {
            log.error("💥 Error creating payment", e);
            return ResponseEntity.status(503)
                    .body(Map.of("error", "Payment service unavailable"));
        }
    }

    private String createYookassaPayment(Long courseId,
                                         BigDecimal amount,
                                         Long userId,
                                         String returnUrl) throws Exception {

        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // Тело запроса к ЮKassa
        Map<String, Object> body = new HashMap<>();

        Map<String, Object> amountNode = new HashMap<>();
        amountNode.put("value", amount.toString());
        amountNode.put("currency", "RUB");
        body.put("amount", amountNode);

        Map<String, Object> confirmationNode = new HashMap<>();
        confirmationNode.put("type", "redirect");
        confirmationNode.put("return_url", returnUrl);
        body.put("confirmation", confirmationNode);

        body.put("capture", true);
        body.put("description", "Course #" + courseId);

        // metadata на будущее для вебхуков
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("courseId", courseId);
        body.put("metadata", metadata);

        String json = objectMapper.writeValueAsString(body);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.yookassa.ru/v3/payments"))
                .header("Authorization", "Basic " + base64Auth)
                .header("Idempotence-Key", idempotenceKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        String bodyStr = response.body() != null ? response.body() : "";
        log.info("YooKassa response: {} {}",
                response.statusCode(),
                bodyStr.substring(0, Math.min(200, bodyStr.length())));

        int statusCode = response.statusCode();

        // По доке YooKassa успешное создание платежа возвращает 200 (иногда 201)
        if (statusCode != 200 && statusCode != 201) {
            throw new RuntimeException(
                    "YooKassa HTTP " + statusCode + " body: " + bodyStr
            );
        }

        JsonNode root = objectMapper.readTree(bodyStr);
        JsonNode confirmation = root.path("confirmation");
        String url = confirmation.path("confirmation_url").asText(null);

        if (url == null || url.isBlank()) {
            throw new RuntimeException(
                    "No confirmation_url in YooKassa response: " + bodyStr
            );
        }

        return url;
    }
}