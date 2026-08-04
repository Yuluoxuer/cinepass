package com.cinepass;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot Admin Template — add business packages under controller/service/entity.
 */
@SpringBootApplication
@MapperScan("com.cinepass.mapper")
@EnableAsync
public class CinePassApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinePassApplication.class, args);
    }
}
