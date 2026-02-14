package com.example.courseplatform.repository;

import com.example.courseplatform.model.Subscription;
import com.example.courseplatform.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUser(User user);
    boolean existsByUserAndCourseId(User user, Long courseId);

    List<Subscription> findByUserAndStatus(User user, String status);
}

