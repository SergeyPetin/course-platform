package com.example.courseplatform.controller;

import com.example.courseplatform.dto.UpdateCourseDto;
import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Lesson;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.LessonRepository;
import com.example.courseplatform.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/courses")
@Tag(name = "Курсы", description = "Управление курсами")
public class CourseController {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final Logger logger =
            LoggerFactory.getLogger(CourseController.class);

    public CourseController(CourseRepository courseRepository, UserRepository userRepository, LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
    }

    @GetMapping
    public Page<Course> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "title") String sortBy
    ) {
        return courseRepository.findAll(PageRequest.of(page, size, Sort.by(sortBy)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> ResponseEntity.ok(course))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Course> createCourse(@RequestBody @Valid Course course) {
        if (course.getTitle() == null || course.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().build();
        }

        // Автор из JWT
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> authorOpt = userRepository.findByEmail(email);
        if (authorOpt.isPresent()) {
            course.setAuthor(authorOpt.get());
        }

        course.setCreatedAt(LocalDateTime.now());
        Course savedCourse = courseRepository.save(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCourse);
    }


    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Course> updateCourse(
            @PathVariable Long id,
            @RequestBody UpdateCourseDto updatedCourse
    ) {
        return courseRepository.findById(id)
                .map(existingCourse -> {
                    // ✅ Обновляем ВСЕ поля (null тоже!)
                    if (updatedCourse.getTitle() != null) {
                        existingCourse.setTitle(updatedCourse.getTitle().trim());
                    }
                    if (updatedCourse.getDescription() != null) {
                        existingCourse.setDescription(updatedCourse.getDescription().trim());
                    }
                    if (updatedCourse.getPrice() != null && updatedCourse.getPrice().compareTo(BigDecimal.ZERO) >= 0) {
                        existingCourse.setPrice(updatedCourse.getPrice());
                    }

                    existingCourse.setCoverImageUrl(updatedCourse.getCoverImageUrl());
                    existingCourse.setPreviewVideoUrl(updatedCourse.getPreviewVideoUrl());

                    Course savedCourse = courseRepository.save(existingCourse);
                    return ResponseEntity.ok(savedCourse);  // ← Method reference!
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        if (courseRepository.existsById(id)) {
            courseRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{courseId}/lessons")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Lesson> createLesson(
            @PathVariable Long courseId,
            @RequestBody Map<String, String> lessonData) {

        String currentUserEmail = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        logger.info("🔍 CREATE LESSON: courseId={}, userEmail={}", courseId, currentUserEmail);

        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Course course = courseOpt.get();

        // ✅ Проверка автора
        if (course.getAuthor() == null || course.getAuthor().getEmail() == null) {
            logger.warn("🚫 NO AUTHOR: courseId={}", courseId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!course.getAuthor().getEmail().equals(currentUserEmail)) {
            logger.warn("🚫 ACCESS DENIED: user={} != author={}",
                    currentUserEmail, course.getAuthor().getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // ✅ Создаем урок
        Lesson lesson = new Lesson();
        lesson.setTitle(lessonData.get("title"));
        lesson.setVideoId(lessonData.get("videoId"));
        lesson.setCourse(course);
        lesson.setOrderNumber((int) (lessonRepository.count() + 1));

        Lesson saved = lessonRepository.save(lesson);
        logger.info("✅ LESSON CREATED: lessonId={}, order={}", saved.getId(), saved.getOrderNumber());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/{courseId}/lessons")
    public ResponseEntity<List<Lesson>> getCourseLessons(@PathVariable Long courseId) {
        List<Lesson> lessons = lessonRepository.findByCourse_IdOrderByOrderNumberAsc(courseId);
        return ResponseEntity.ok(lessons);
    }

    @PutMapping("/{courseId}/lessons/{lessonId}")
    public ResponseEntity<Lesson> updateLesson(
            @PathVariable Long courseId,
            @PathVariable Long lessonId,
            @RequestBody Lesson updatedLesson
    ) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getCourse().getId().equals(courseId))
                .map(lesson -> {
                    if (updatedLesson.getTitle() != null) lesson.setTitle(updatedLesson.getTitle());
                    if (updatedLesson.getVideoId() != null) lesson.setVideoId(updatedLesson.getVideoId());
                    if (updatedLesson.getOrderNumber() != null) lesson.setOrderNumber(updatedLesson.getOrderNumber());
                    if (updatedLesson.getDurationMinutes() > 0)
                        lesson.setDurationMinutes(updatedLesson.getDurationMinutes());
                    return lessonRepository.save(lesson);
                })
                .map(ResponseEntity::ok)  // ← Method reference!
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{courseId}/lessons/{lessonId}")
    public ResponseEntity<Void> deleteLesson(
            @PathVariable Long courseId,
            @PathVariable Long lessonId
    ) {
        Optional<Lesson> lessonOpt = lessonRepository.findById(lessonId);
        if (lessonOpt.isPresent()) {
            Lesson lesson = lessonOpt.get();
            if (lesson.getCourse().getId().equals(courseId)) {
                lessonRepository.delete(lesson);
                return ResponseEntity.noContent().build();
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{courseId}/lessons/{lessonId}")
    public ResponseEntity<Lesson> getLessonById(
            @PathVariable Long courseId,
            @PathVariable Long lessonId
    ) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getCourse().getId().equals(courseId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("permitAll()")
    public ResponseEntity<String> createSubscription(@RequestBody Map<String, Object> data) {
        logger.info("🛒 Покупка: courseId={}, userId={}", data.get("courseId"), data.get("userId"));
        return ResponseEntity.ok("Покупка сохранена! Курс ID: " + data.get("courseId"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Course>> getMyCourses() {
        // SecurityContextHolder.getContext().getAuthentication().getName()
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Course> courses = courseRepository.findByAuthorEmail(email);
        return ResponseEntity.ok(courses);
    }

}


