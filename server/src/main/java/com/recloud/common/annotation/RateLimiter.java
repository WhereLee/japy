package com.recloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * <p>
 * 支持两种限流算法：
 * - FIXED_WINDOW：固定窗口（INCR + EXPIRE，简单高效，有边界突发问题）
 * - SLIDING_WINDOW：滑动窗口（ZSET + Lua，精度高，Redis 操作稍多）
 * <p>
 * 限流维度：优先按 userId（已登录），回退到 IP（未登录）。
 * <p>
 * 降级策略（strict 属性）：
 * - strict=true（写操作）：Redis 挂了 → 拒绝请求（防止刷接口产生脏数据）
 * - strict=false（读操作）：Redis 挂了 → 放行（宁可多查几次 DB，不能拒绝用户）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {
    /** 限流阈值 */
    int limit() default 10;
    /** 时间窗口（秒） */
    int time() default 60;
    /** 限流 key（默认用 IP + 方法名） */
    String key() default "";
    /** 限流算法，默认滑动窗口 */
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.SLIDING_WINDOW;
    /**
     * 严格模式（Redis 挂了时的行为）
     * - true：拒绝请求（适用于写操作，如创建批注、点赞）
     * - false：放行请求（适用于读操作，如查询列表）
     */
    boolean strict() default false;

    enum RateLimitAlgorithm {
        /** 固定窗口：INCR + EXPIRE，简单高效 */
        FIXED_WINDOW,
        /** 滑动窗口：ZSET + Lua，精度更高 */
        SLIDING_WINDOW
    }
}
