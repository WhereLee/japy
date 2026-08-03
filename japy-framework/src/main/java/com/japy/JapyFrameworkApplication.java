package com.japy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.japy.**.mapper")
public class JapyFrameworkApplication {
    public static void main(String[] args) {
        SpringApplication.run(JapyFrameworkApplication.class, args);
    }
}
