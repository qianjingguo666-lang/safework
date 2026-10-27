package com.safework.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.safework")
@MapperScan("com.safework.**.mapper")
public class SafeworkApplication {
    public static void main(String[] args) {
        SpringApplication.run(SafeworkApplication.class, args);
    }
}
