package com.example.courseplatform.controller;

import com.example.courseplatform.dto.SubscriptionDto;
import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Subscription;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.SubscriptionRepository;
import com.example.courseplatform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionsController {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public SubscriptionsController(SubscriptionRepository subscriptionRepository,
                                   UserRepository userRepository,
                                   CourseRepository courseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @PostMapping
    public ResponseEntity<?> subscribe(@RequestBody java.util.Map<String, String> request,
                                       Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("Пользователь не найден")
        );

        Long courseId = Long.valueOf(request.get("courseId"));
        Course course = courseRepository.findById(courseId).orElseThrow(
                () -> new RuntimeException("Курс не найден")
        );

        if (subscriptionRepository.existsByUserAndCourseId(user, courseId)) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "Уже подписан на этот курс"));
        }

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setCourse(course);
        subscription.setStatus("ACTIVE");
        subscription.setPurchaseDate(LocalDateTime.now());
        subscriptionRepository.save(subscription);

        return ResponseEntity.ok(java.util.Map.of("message", "Подписка оформлена"));
    }

    @GetMapping("/my")
    public ResponseEntity<List<SubscriptionDto>> mySubscriptions(Authentication auth) {
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Subscription> subscriptions =
                subscriptionRepository.findByUserAndStatus(user, "ACTIVE");

        List<SubscriptionDto> dtoList = subscriptions.stream()
                .map(sub -> new SubscriptionDto(
                        sub.getId(),
                        sub.getCourse() != null ? sub.getCourse().getId() : null,
                        sub.getStatus(),
                        sub.getPurchaseDate(),
                        sub.getExpiresAt()
                ))
                .toList();

        return ResponseEntity.ok(dtoList);
    }
}