package com.japy.aspect;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * XSS 校验注解：标注在 DTO 字符串字段上，命中危险脚本模式直接拒绝请求
 * （存储型 XSS 防御：入口拒绝而非入库转义，保证数据原始性）
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = XssValidator.class)
public @interface Xss {
    String message() default "内容包含非法字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
