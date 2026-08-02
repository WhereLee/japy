package com.japy.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端操作日志注解：标注在管理端写操作接口方法上，
 * 由 AdminLogAspect 自动记录操作人/参数/耗时/结果。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminLog {

    /** 操作名（沿用既有语义，如 ban_user / hide_moment / resolve_report） */
    String action();
}
