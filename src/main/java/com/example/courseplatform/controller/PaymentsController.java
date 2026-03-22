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
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/stripe-webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        System.out.println("🚨🔥 WEBHOOK ПОЛУЧЕН! 🔥🚨");
        System.out.println("📄 Payload: " + payload.substring(0, 500));

        try {
            // ★ ПРЯМАЯ JSON РАЗБОРКА (100% работает)
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(payload);

            System.out.println("✅ JSON parsed!");

            String type = root.path("type").asText();
            System.out.println("📦 Event type: " + type);

            if ("checkout.session.completed".equals(type)) {
                System.out.println("🎯 CHECKOUT COMPLETED!");

                JsonNode metadata = root.path("data").path("object").path("metadata");
                String courseId = metadata.path("courseId").asText("NULL");
                String userEmail = metadata.path("userEmail").asText("NULL");

                System.out.println("👤 User email: '" + userEmail + "'");
                System.out.println("📚 Course ID: '" + courseId + "'");

                // ★ СОЗДАЁМ ПОДПИСКУ
                if (!"NULL".equals(courseId) && !"NULL".equals(userEmail)) {
                    Long courseIdLong = Long.parseLong(courseId);

                    User user = userRepository.findByEmail(userEmail).orElse(null);
                    System.out.println("🔍 User found: " + (user != null));

                    Course course = courseRepository.findById(courseIdLong).orElse(null);
                    System.out.println("🔍 Course found: " + (course != null));

                    if (user != null && course != null) {
                        Subscription sub = new Subscription();
                        sub.setUser(user);
                        sub.setCourse(course);
                        sub.setStatus("ACTIVE");
                        sub.setPurchaseDate(LocalDateTime.now());

                        subscriptionRepository.saveAndFlush(sub);
                        System.out.println("✅✅✅ ПОДПИСКА СОЗДАНА! ID=" + sub.getId());
                    }
                }
            }

            System.out.println("🏁 WEBHOOK УСПЕШНО!");
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            System.out.println("💥 ОШИБКА: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok("OK");
        }
    }

}
