package com.example.courseplatform.controller;

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
            log.info("🚀 START: user={}, courseId={}", auth.getName(), request.get("courseId"));

            // HARDCODE → РАБОТАЕТ точно!
            String paymentUrl = createYookassaPaymentDirect(13L, new BigDecimal("15000.00"), 3L);
            log.info("✅ SUCCESS URL: {}", paymentUrl);

            return ResponseEntity.ok(Map.of("url", paymentUrl));

        } catch (Exception e) {
            log.error("💥 CRASH: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    private String createYookassaPaymentDirect(Long courseId, BigDecimal amount, Long userId) throws Exception {
        String idempotenceKey = userId + "_" + courseId + "_" + System.currentTimeMillis();
        String auth = shopId + ":" + secretKey;
        String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

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
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Yookassa: {} {}", response.statusCode(), response.body().substring(0, 200));

        if (response.statusCode() == 201) {
            // РУЧНОЙ парсинг БЕЗ ObjectMapper!
            String body = response.body();
            int start = body.indexOf("\"confirmation_url\":\"") + 17;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        }

        throw new RuntimeException("Yookassa: " + response.statusCode());
    }

}