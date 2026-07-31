package com.minihr;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Spring Boot Admin Template — add business packages under controller/service/entity.
 */
@SpringBootApplication
@MapperScan("com.minihr.mapper")
@EnableAsync
public class MinniHrApplication {

    public static void main(String[] args) {
        SpringApplication.run(MinniHrApplication.class, args);
    }
}
