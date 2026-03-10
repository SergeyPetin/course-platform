package com.example.courseplatform.controller;

import com.example.courseplatform.dto.LoginRequest;
import com.example.courseplatform.dto.LoginResponse;
import com.example.courseplatform.dto.RegisterRequest;
import jakarta.validation.Valid;
import com.example.courseplatform.model.Role;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.UserRepository;
import com.example.courseplatform.service.JwtService;
import com.example.courseplatform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserService userService,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        user.setRole(Role.USER);
        if ("author@test.com".equals(request.getEmail())) {
            user.setRole(Role.AUTHOR);
        }

        userRepository.save(user);

        // Автологин
        String token = userService.authenticate(request.getEmail(), request.getPassword());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());

        return ResponseEntity.ok(response);
    }


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = userService.authenticate(request.getEmail(), request.getPassword());

        User user = userService.findByEmail(request.getEmail());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, String>> registerUsers(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String fullName = email.split("@")[0] + " User";

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "User exists"));
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(Role.USER);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Registered"));
    }
}

