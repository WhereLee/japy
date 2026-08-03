package com.japy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在管理端写操作方法上，
 * 由 OperLogAspect 记录 操作人/参数/耗时/结果 → MQ 异步落库（失败降级同步）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块标题，如 "用户管理" */
    String title();

    /** 业务类型：1新增 2修改 3删除 0其他 */
    int businessType() default 0;
}
