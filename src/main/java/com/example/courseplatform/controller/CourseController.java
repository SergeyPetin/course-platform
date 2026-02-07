package com.example.courseplatform.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.example.courseplatform.dto.UpdateCourseDto;
import com.example.courseplatform.model.Course;
import com.example.courseplatform.model.Lesson;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.CourseRepository;
import com.example.courseplatform.repository.LessonRepository;
import com.example.courseplatform.repository.UserRepository;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/courses")
@Tag(name = "Курсы", description = "Управление курсами")
public class CourseController {


    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    public CourseController(CourseRepository courseRepository, UserRepository userRepository, LessonRepository lessonRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
    }

    // ✅ ЕДИНСТВЕННЫЙ getAllCourses с пагинацией и сортировкой
    @GetMapping
    public Page<Course> getAllCourses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort
    ) {
        return courseRepository.findAll(PageRequest.of(page, size, Sort.by(sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> ResponseEntity.ok(course))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Course> createCourse(@RequestBody @Valid Course course) {

        // ✅ Валидация через @Valid + модель (title, price @NotNull)
        if (course.getTitle() == null || course.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(null);  // Или Map.of("error", "Title required")
        }
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest()
                    .body(null);  // Или Map.of("error", "Price >= 0")
        }

        // 🔥 НОВОЕ: автор = текущий логин из токена!
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User author = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Author not found: " + email));

        course.setAuthor(author);
        course.setCreatedAt(LocalDateTime.now());

        Course savedCourse = courseRepository.save(course);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCourse);
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateCourse(
            @PathVariable Long id,
            @RequestBody UpdateCourseDto updatedCourse
    ) {
        return courseRepository.findById(id)
                .map(existingCourse -> {
                    if (updatedCourse.getTitle() != null && !updatedCourse.getTitle().trim().isEmpty())
                        existingCourse.setTitle(updatedCourse.getTitle().trim());

                    if (updatedCourse.getDescription() != null && !updatedCourse.getDescription().trim().isEmpty())
                        existingCourse.setDescription(updatedCourse.getDescription().trim());

                    if (updatedCourse.getPrice() != null) {
                        if (updatedCourse.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                            return ResponseEntity.badRequest().body("Цена не может быть отрицательной");
                        }
                        existingCourse.setPrice(updatedCourse.getPrice());
                    }

                    if (updatedCourse.getAuthor() != null && updatedCourse.getAuthor().getId() != null) {
                        userRepository.findById(updatedCourse.getAuthor().getId())
                                .ifPresent(existingCourse::setAuthor);
                    }

                    Course savedCourse = courseRepository.save(existingCourse);
                    return ResponseEntity.ok(savedCourse);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id) {
        if (courseRepository.existsById(id)) {
            courseRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ✅ Lessons CRUD
    @PostMapping("/{courseId}/lessons")
    public ResponseEntity<Lesson> createLesson(
            @PathVariable Long courseId,
            @RequestBody Lesson lesson
    ) {
        return courseRepository.findById(courseId)
                .map(course -> {
                    lesson.setCourse(course);
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(lessonRepository.save(lesson));
                })
                .orElse(ResponseEntity.notFound().build());
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
                    if (updatedLesson.getVideoUrl() != null) lesson.setVideoUrl(updatedLesson.getVideoUrl());
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
}


