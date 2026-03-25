package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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

    private final SubscriptionRepository subscriptionRepository; // TODO: использовать позже
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
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

            Long courseId = Long.valueOf(request.get("courseId").toString());
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("Курс не найден"));

            String paymentUrl = createYookassaPayment(courseId, course.getPrice(), user.getId());

            log.info("Payment created: userId={}, url={}", user.getId(), paymentUrl);
            return ResponseEntity.ok(Map.of("url", paymentUrl));
        } catch (IllegalArgumentException e) {
            log.warn("Payment validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("YooKassa payment failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Payment creation failed"));
        }
    }

    private String createYookassaPayment(Long courseId, BigDecimal amount, Long userId) throws Exception {
        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        String json = """
        {
            "amount": {"value": "%s", "currency": "RUB"},
            "confirmation": {"type": "redirect", "return_url": "https://front-production-c924.up.railway.app/courses/%d"},
            "capture": true,
            "description": "Course #%d",
            "metadata": {"userId": "%d", "courseId": "%d"}
        }
        """.formatted(amount.toString(), courseId, courseId, userId, courseId);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.yookassa.ru/v3/payments"))
                .header(HttpHeaders.AUTHORIZATION, "Basic " + base64Auth)
                .header("Idempotence-Key", idempotenceKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            log.error("YooKassa API error: {} {}", response.statusCode(), response.body());
            throw new RuntimeException("YooKassa API error: " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        String confirmationUrl = node.path("confirmation").path("confirmation_url").asText();

        log.info("YooKassa API success: {}", confirmationUrl);
        return confirmationUrl;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleYookassaWebhook(HttpServletRequest request) {
        try {
            String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("YooKassa webhook: {}", payload.substring(0, 200));

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
            log.info("Webhook: paymentId={} status={}", paymentId, status);
            // TODO: create Subscription from metadata
        } catch (Exception e) {
            log.error("Webhook parse failed", e);
        }
    }
}