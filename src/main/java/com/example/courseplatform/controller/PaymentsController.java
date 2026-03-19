package com.example.courseplatform.controller;

import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Subscription;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.SubscriptionRepository;
import com.example.courseplatform.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
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
                            .setCurrency("usd")
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

    // 2) Webhook от Stripe
    @PostMapping("/stripe-webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader
    ) {
        try {
            // Проверяем подпись Stripe
            Event event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    stripeWebhookSecret
            );

            if ("checkout.session.completed".equals(event.getType())) {
                EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

                if (deserializer.getObject().isPresent()
                        && deserializer.getObject().get() instanceof Session) {

                    Session session = (Session) deserializer.getObject().get();

                    String courseIdStr = session.getMetadata().get("courseId");
                    String userEmail = session.getMetadata().get("userEmail");

                    if (courseIdStr != null && userEmail != null) {
                        Long courseId = Long.valueOf(courseIdStr);

                        User user = userRepository.findByEmail(userEmail)
                                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

                        Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new RuntimeException("Курс не найден"));

                        // Создаём подписку, если её ещё нет
                        boolean exists = subscriptionRepository
                                .existsByUserAndCourseId(user, courseId);

                        if (!exists) {
                            Subscription sub = new Subscription();
                            sub.setUser(user);
                            sub.setCourse(course);
                            sub.setStatus("ACTIVE");
                            sub.setPurchaseDate(LocalDateTime.now());
                            subscriptionRepository.save(sub);
                        }
                    }
                }
            }

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            // В лог — а Stripe вернём 400, чтобы он при желании повторил запрос
            return ResponseEntity.status(400).body("Webhook error: " + e.getMessage());
        }
    }
}
