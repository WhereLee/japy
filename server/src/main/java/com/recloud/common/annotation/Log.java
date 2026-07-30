package com.recloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 * <p>
 * 标注在 Controller 方法上，AOP 切面异步记录操作日志到数据库。
 * 支持配置是否记录请求参数和响应结果，敏感字段自动脱敏。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    String module() default "";
    String operation() default "";
    boolean saveRequestData() default true;
    boolean saveResponseData() default false;
}
