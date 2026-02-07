package com.example.courseplatform.controller;

import org.springframework.security.core.Authentication;
import com.example.courseplatform.model.Role;
import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.UserRepository;
import com.example.courseplatform.service.JwtService;
import com.example.courseplatform.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ✅ Constructor injection вместо field injection
    @Autowired
    public AuthController(AuthenticationManager authenticationManager,
                          UserService userService,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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

        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Пользователь зарегистрирован"));  // ✅ "message"
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        // ✅ ГЛАВНОЕ ИСПРАВЛЕНИЕ: загружаем UserDetails для jwtService
        UserDetails userDetails = userService.loadUserByUsername(email);
        String jwtToken = jwtService.generateToken(userDetails);  // Теперь UserDetails!

        return ResponseEntity.ok(Map.of("token", jwtToken, "message", "Авторизация успешна"));
    }
}
