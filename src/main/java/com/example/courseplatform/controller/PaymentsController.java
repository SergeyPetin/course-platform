package com.example.courseplatform.controller;

import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Subscription;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.SubscriptionRepository;
import com.example.courseplatform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.stripe.model.Event;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentsController {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    @Value("${app.frontend.success-url}")
    private String successUrl;

    @Value("${app.frontend.cancel-url}")
    private String cancelUrl;

    public PaymentsController(SubscriptionRepository subscriptionRepository,
                              UserRepository userRepository,
                              CourseRepository courseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    // 1) Создание Stripe Checkout Session
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth
    ) {
        try {
            Stripe.apiKey = stripeSecretKey;

            Long courseId = Long.valueOf(request.get("courseId").toString());
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Курс не найден"));

            String email = auth.getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            long amountInCents = course.getPrice()
                    .multiply(new BigDecimal("100"))
                    .longValue();

            SessionCreateParams.LineItem.PriceData.ProductData productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(course.getTitle())
                            .build();

            SessionCreateParams.LineItem.PriceData priceData =
                    SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("rub")
                            .setUnitAmount(amountInCents)
                            .setProductData(productData)
                            .build();

            SessionCreateParams.LineItem lineItem =
                    SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(priceData)
                            .build();

            SessionCreateParams params = SessionCreateParams.builder()
                    .addLineItem(lineItem)
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(cancelUrl)
                    // ВАЖНО: кладём данные для вебхука
                    .putMetadata("courseId", courseId.toString())
                    .putMetadata("userEmail", user.getEmail())
                    .build();

            Session session = Session.create(params);

            return ResponseEntity.ok(Map.of("url", session.getUrl()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(500)
                    .body(Map.of("error", "Ошибка создания платежа: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/stripe-webhook", consumes = "application/json")
    public ResponseEntity<String> handleStripeWebhook(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            // ★ RAW BODY
            String payload;
            try (InputStream is = request.getInputStream()) {
                payload = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            System.out.println("🚨 WEBHOOK RAW: " + payload.substring(0, 400));

            // ★ STRIPE_WEBHOOK_SECRET из Railway
            String webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
            System.out.println("🔑 Secret loaded: " + (webhookSecret != null ? "YES" : "NO"));

            // ★ ПРОВЕРКА SIGNATURE
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            System.out.println("✅ Signature OK! Event: " + event.getType());

            if ("checkout.session.completed".equals(event.getType())) {
                System.out.println("🎯 checkout.session.completed!");

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(payload);
                    JsonNode metadata = root.path("data").path("object").path("metadata");

                    String courseId = metadata.path("courseId").asText(null);
                    String userEmail = metadata.path("userEmail").asText(null);

                    System.out.println("📦 courseId='" + courseId + "'");
                    System.out.println("👤 userEmail='" + userEmail + "'");

                    if (courseId != null && userEmail != null) {
                        Long cid = Long.parseLong(courseId);
                        User user = userRepository.findByEmail(userEmail).orElse(null);
                        Course course = courseRepository.findById(cid).orElse(null);

                        System.out.println("🔍 User found: " + (user != null));
                        System.out.println("🔍 Course found: " + (course != null));

                        if (user != null && course != null) {
                            if (!subscriptionRepository.existsByUserAndCourseId(user, cid)) {
                                Subscription sub = new Subscription();
                                sub.setUser(user);
                                sub.setCourse(course);
                                sub.setStatus("ACTIVE");
                                sub.setPurchaseDate(LocalDateTime.now());
                                subscriptionRepository.saveAndFlush(sub);
                                System.out.println("✅✅✅ ПОДПИСКА СОЗДАНА! ID=" + sub.getId());
                            } else {
                                System.out.println("⚠️ Подписка уже есть");
                            }
                        } else {
                            System.out.println("❌ User или Course не найдены");
                        }
                    } else {
                        System.out.println("❌ Metadata пустые: courseId=" + courseId + ", userEmail=" + userEmail);
                    }
                } catch (Exception ex) {
                    System.out.println("💥 ERROR INSIDE checkout.session.completed: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            return ResponseEntity.ok("OK");

        } catch (SignatureVerificationException e) {
            System.out.println("💥 SIGNATURE FAILED!");
            return ResponseEntity.status(400).body("Signature failed");
        } catch (Exception e) {
            System.out.println("💥 GENERAL ERROR: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("OK");
        }
    }

}
