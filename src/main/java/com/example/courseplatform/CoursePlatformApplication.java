package com.example.courseplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = "com.example.courseplatform")
@EnableJpaRepositories(basePackages = "com.example.courseplatform.repository")
public class CoursePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoursePlatformApplication.class, args);
    }
}

