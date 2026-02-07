package com.example.courseplatform.repository;

import com.example.courseplatform.model.Subacription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subacription, Long>{
}
