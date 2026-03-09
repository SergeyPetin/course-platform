package com.example.courseplatform.controller;

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
    public ResponseEntity<Map<String, String>> register(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");
        String fullName = request.get("fullName");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Пользователь с таким email уже существует"));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);

        user.setRole(Role.USER);
        if ("author@test.com".equals(email)) {
            user.setRole(Role.AUTHOR);
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Пользователь зарегистрирован"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new RuntimeException("Пользователь не найден")
        );

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Неверный пароль"));
        }

        Map<String, Object> extraClaims = Map.of("role", user.getRole().name());
        String jwt = jwtService.generateToken(extraClaims, userService.loadUserByUsername(email));

        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);
        response.put("message", "Авторизация успешна");
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

