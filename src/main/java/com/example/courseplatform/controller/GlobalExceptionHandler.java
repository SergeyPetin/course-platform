package com.example.courseplatform.controller;

import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

public class GlobalExceptionHandler {

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Map<String, String>>
    handlePropertyReference(PropertyReferenceException e) {
        return ResponseEntity.ok(Map.of("Ошибка", "Недопустимое поле сортировки"));
    }
}
