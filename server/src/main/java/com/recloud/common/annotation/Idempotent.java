package com.recloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解
 * <p>
 * 基于 Redis SETNX 实现接口级幂等控制。
 * 在指定 TTL 窗口内，相同 key 的请求只允许执行一次。
 * <p>
 * 使用方式：
 * <pre>
 * // SpEL 表达式动态构建 key
 * &#064;Idempotent(key = "#chapterId + ':' + #userId + ':' + #content", ttl = 30)
 * public Annotation create(Long chapterId, Long userId, String content) { ... }
 * </pre>
 * <p>
 * 设计要点：
 * - key 支持 SpEL 表达式，灵活组合参数
 * - TTL 窗口内相同 key 只放行一次
 * - 窗口结束后自动清理，不占用 Redis 内存
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * 幂等 key（SpEL 表达式）
     * <p>
     * 示例：
     * - "#chapterId + ':' + #userId" — 按章节+用户去重
     * - "#request.username" — 按用户名去重
     */
    String key();

    /**
     * 幂等窗口时间（秒），默认 30s
     */
    long ttl() default 30;

    /**
     * 重复请求时的提示信息
     */
    String message() default "请勿重复提交";
}
