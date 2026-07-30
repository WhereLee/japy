package com.recloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.recloud.mapper", "com.recloud.common.mapper"})
public class RecloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecloudApplication.class, args);
    }
}
