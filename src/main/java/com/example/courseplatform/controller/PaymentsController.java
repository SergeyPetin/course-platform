package com.example.courseplatform.controller;

import com.example.courseplatform.model.*;
import com.example.courseplatform.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@Tag(name = "Платежи")
public class PaymentsController {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    // ЮKassa
    @Value("${yookassa.shop-id:}")
    private String yookassaShopId;
    @Value("${yookassa.secret-key:}")
    private String yookassaSecretKey;

    // Stripe
    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    public PaymentsController(SubscriptionRepository subscriptionRepository,
                              UserRepository userRepository,
                              CourseRepository courseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestBody Map<String, Object> request,
            Authentication auth) {

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Long courseId = Long.valueOf((Integer) request.get("courseId"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Курс не найден"));

        // Мок — возвращаем URL оплаты
        String paymentUrl = "https://yookassa.ru/pay/" + courseId + "/" + user.getId();

        if (!"".equals(stripeSecretKey)) {
            paymentUrl = "https://stripe.checkout/" + courseId;
        }

        return ResponseEntity.ok(Map.of(
                "paymentUrl", paymentUrl,
                "courseId", courseId.toString(),
                "amount", course.getPrice().toString()
        ));
    }

    // Webhook (позже)
    @PostMapping("/success")
    public ResponseEntity<Map<String, String>> paymentSuccess(
            @RequestParam String courseId,
            @RequestParam String userId,
            Authentication auth) {

        // Создать подписку
        User user = userRepository.findById(Long.valueOf(userId)).orElseThrow();
        Course course = courseRepository.findById(Long.valueOf(courseId)).orElseThrow();

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setCourse(course);
        sub.setStatus("ACTIVE");
        sub.setPurchaseDate(LocalDateTime.now());
        subscriptionRepository.save(sub);

        return ResponseEntity.ok(Map.of("status", "Подписка создана!"));
    }
}
