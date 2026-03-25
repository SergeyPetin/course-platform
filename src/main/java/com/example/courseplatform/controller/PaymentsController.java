package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${yookassa.secret-key}")
    private String secretKey;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth
    ) {
        try {
            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            Long courseId = Long.valueOf(request.get("courseId").toString());
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Курс не найден"));

            String paymentUrl = createYookassaPayment(courseId, course.getPrice(), user.getId());

            return ResponseEntity.ok(Map.of("url", paymentUrl));
        } catch (Exception e) {
            log.error("ЮKassa payment failed", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ошибка создания платежа: " + e.getMessage()));
        }
    }

    private String createYookassaPayment(Long courseId, BigDecimal amount, Long userId) throws Exception {
        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());

        String json = """
    {
        "amount": {"value": "%s", "currency": "RUB"},
        "confirmation": {"type": "redirect", "return_url": "https://front-production-c924.up.railway.app/courses/%d"},
        "capture": true,
        "description": "Курс #%d",
        "metadata": {"userId": "%d", "courseId": "%d"}
    }
    """.formatted(amount, courseId, courseId, userId, courseId);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.yookassa.ru/v3/payments"))
                .header("Authorization", "Basic " + base64Auth)
                .header("Idempotence-Key", idempotenceKey)
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(response.body());
        String confirmationUrl = node.path("confirmation").path("confirmation_url").asText();

        log.info("✅ ЮKassa API success: {}", confirmationUrl);
        return confirmationUrl;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleYookassaWebhook(HttpServletRequest request) {
        try {
            String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("🔥 ЮKassa webhook: {}", payload.substring(0, 200));

            JsonNode root = objectMapper.readTree(payload);
            activateSubscription(root);

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Webhook error", e);
            return ResponseEntity.ok("OK");
        }
    }

    private void activateSubscription(JsonNode event) {
        try {
            String paymentId = event.path("object").path("id").asText();
            String status = event.path("object").path("status").asText();

            log.info("✅ Webhook paymentId={} status={}", paymentId, status);
            // TODO: парсинг label → создание Subscription
        } catch (Exception e) {
            log.error("Webhook parse failed", e);
        }
    }
}