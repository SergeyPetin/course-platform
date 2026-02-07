package com.example.courseplatform.repository;

import com.example.courseplatform.model.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourse_IdOrderByOrderNumberAsc(Long courseId);
}

