package com.recloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁注解
 * <p>
 * 基于 Redis SETNX + EXPIRE 实现简单分布式锁。
 * Key 自动拼接方法名和参数，防止并发操作数据不一致。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /** 锁 key 前缀 */
    String prefix() default "";
    /** 锁过期时间（秒） */
    int expire() default 10;
    /** 获取锁失败提示信息 */
    String message() default "操作过于频繁，请稍后再试";
}
