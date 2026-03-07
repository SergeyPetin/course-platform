package com.example.courseplatform.repository;

import com.example.courseplatform.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByAuthorEmail(String email);

}
