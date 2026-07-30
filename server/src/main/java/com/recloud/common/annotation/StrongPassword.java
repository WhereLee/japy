package com.recloud.common.annotation;

import com.recloud.common.validator.StrongPasswordValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 密码强度校验注解
 * <p>
 * 要求：至少 8 位，包含大写字母、小写字母和数字。
 * 用法：在 DTO 的 password 字段上加 @StrongPassword。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "密码至少8位，需包含大小写字母和数字";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
