package com.example.courseplatform.service;

import com.example.courseplatform.model.User;
import com.example.courseplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        System.out.println("🔍 DEBUG loadUserByUsername: " + email);  // Твои логи
        Optional<User> userOpt = userRepository.findByEmail(email);
        System.out.println("🔍 DEBUG User found: " + userOpt.isPresent() + " " +
                userOpt.map(User::getEmail).orElse("NULL"));

        User user = userOpt.orElseThrow(() ->
                new UsernameNotFoundException("Пользователь с email: " + email + " не найден"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())  // Уже закодирован в БД
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void saveUser(User user) {
        userRepository.save(user);
    }
}
