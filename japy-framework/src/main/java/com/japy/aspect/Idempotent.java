package com.japy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等（Redis SETNX）：同一用户短时间内重复提交同一请求（相同参数）只放行第一次。
 * 用于新增类写操作（用户新增/角色新增/公告新增等），防重复提交。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** 幂等窗口（秒），窗口内相同请求视为重复 */
    int expireSeconds() default 10;
}
