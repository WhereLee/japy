package com.recloud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 自定义健康检查：验证小说目录可访问
 *
 * /actuator/health 会显示：
 * {
 *   "status": "UP",
 *   "components": {
 *     "db": { "status": "UP" },           // Spring Boot 自动检测
 *     "redis": { "status": "UP" },         // Spring Boot 自动检测
 *     "novelStorage": { "status": "UP" }   // 自定义检查
 *   }
 * }
 */
@Component
public class NovelStorageHealthIndicator implements HealthIndicator {

    @Value("${novel.txt-dir:./novels}")
    private String txtDir;

    @Override
    public Health health() {
        Path dir = Paths.get(txtDir);
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            return Health.up()
                    .withDetail("path", dir.toAbsolutePath().toString())
                    .withDetail("writable", Files.isWritable(dir))
                    .build();
        }
        return Health.down()
                .withDetail("path", dir.toAbsolutePath().toString())
                .withDetail("error", "小说目录不存在或不可访问")
                .build();
    }
}
