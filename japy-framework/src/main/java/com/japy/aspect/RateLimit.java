package com.japy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（Redisson 令牌桶，按 用户+接口 维度）：
 * 标注在需要限流的接口上，如登录、检索等高频敏感接口。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 每秒许可数（令牌桶速率） */
    double permitsPerSecond() default 10;

    /** 自定义 key 前缀（默认 类名.方法名） */
    String key() default "";
}
