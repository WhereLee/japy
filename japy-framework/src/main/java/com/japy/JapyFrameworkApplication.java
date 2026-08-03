package com.japy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.japy.**.mapper")
public class JapyFrameworkApplication {
    public static void main(String[] args) {
        SpringApplication.run(JapyFrameworkApplication.class, args);
    }
}
