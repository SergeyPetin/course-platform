package com.example.courseplatform.controller;

import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Subscription;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.SubscriptionRepository;
import com.example.courseplatform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class YookassaWebhookController {

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Вебхук ЮKassa: сюда ЮKassa шлёт уведомления о статусах платежей.
     * Этот URL нужно указать в настройках магазина ЮKassa, например:
     * https://bek-production-15ec.up.railway.app/payments/yookassa-webhook
     */
    @PostMapping("/yookassa-webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload,
                                           @RequestHeader Map<String, String> headers) {
        try {
            log.info("📩 YooKassa webhook received: headers={}, body={}",
                    headers, payload);

            JsonNode root = objectMapper.readTree(payload);

            String event = root.path("event").asText(null);
            if (event == null || event.isBlank()) {
                log.warn("⚠️ YooKassa webhook: no event field");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "no event field"));
            }

            // Нас интересует только успешная оплата
            if (!"payment.succeeded".equals(event)) {
                log.info("ℹ️ YooKassa webhook: ignore event {}", event);
                return ResponseEntity.ok(Map.of("status", "ignored", "event", event));
            }

            JsonNode objectNode = root.path("object");
            if (objectNode.isMissingNode()) {
                log.warn("⚠️ YooKassa webhook: no object node");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "no object node"));
            }

            // Проверяем статус внутри объекта платежа
            String status = objectNode.path("status").asText(null);
            if (!"succeeded".equals(status)) {
                log.info("ℹ️ YooKassa webhook: payment status not succeeded: {}", status);
                return ResponseEntity.ok(Map.of("status", "ignored", "paymentStatus", status));
            }

            // metadata: { userId, courseId } — мы кладём это при создании платежа
            JsonNode metadataNode = objectNode.path("metadata");
            Long userId = metadataNode.path("userId").isMissingNode()
                    ? null
                    : metadataNode.path("userId").asLong();
            Long courseId = metadataNode.path("courseId").isMissingNode()
                    ? null
                    : metadataNode.path("courseId").asLong();

            if (userId == null || courseId == null || userId == 0L || courseId == 0L) {
                log.error("❌ YooKassa webhook: missing userId/courseId in metadata: {}", metadataNode);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "missing metadata userId/courseId"));
            }

            log.info("✅ YooKassa payment succeeded: userId={}, courseId={}", userId, courseId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Course not found: " + courseId));

            // Если уже есть активная подписка — просто подтверждаем
            if (subscriptionRepository.existsByUserAndCourseId(user, courseId)) {
                log.info("ℹ️ Subscription already exists for userId={}, courseId={}", userId, courseId);
                return ResponseEntity.ok(Map.of("status", "already_subscribed"));
            }

            // Создаём подписку
            Subscription subscription = new Subscription();
            subscription.setUser(user);
            subscription.setCourse(course);
            subscription.setStatus("ACTIVE");
            subscription.setPurchaseDate(LocalDateTime.now());
            subscription.setExpiresAt(null); // можно добавить срок позже

            subscriptionRepository.save(subscription);

            log.info("🎫 Subscription created: userId={}, courseId={}", userId, courseId);

            return ResponseEntity.ok(Map.of(
                    "status", "subscription_created",
                    "userId", userId,
                    "courseId", courseId
            ));

        } catch (Exception e) {
            log.error("💥 Error handling YooKassa webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "webhook processing error"));
        }
    }
}