package com.example.courseplatform.controller;

import com.example.courseplatform.model.Role;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.UserRepository;
import com.example.courseplatform.service.JwtService;
import com.example.courseplatform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
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
        user.setEmail(request.get("email"));
        user.setPassword(passwordEncoder.encode(password));
        user.setFullName(fullName);
        user.setRole(Role.USER);

        user.setRole("author@test.com".equals(request.get("email")) ? Role.AUTHOR : Role.USER);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Пользователь зарегистрирован"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        // ✅ Аутентифицируем (нужно для проверки пароля), но не сохраняем
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        // Генерируем токен
        UserDetails userDetails = userService.loadUserByUsername(email);
        String jwt = jwtService.generateToken(userDetails);

        Map<String, String> response = new HashMap<>();
        response.put("token", jwt);
        response.put("message", "Авторизация успешна");
        return ResponseEntity.ok(response);
    }
}
