package com.japy.aspect;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * XSS 校验器：拒绝含脚本特征的内容（<script>/<iframe>/javascript:/on事件/alert(）
 */
public class XssValidator implements ConstraintValidator<Xss, String> {

    private static final Pattern DANGEROUS = Pattern.compile(
            "<script|</script|<iframe|javascript\\s*:|on(load|error|click|mouseover|focus)\\s*=|alert\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || !DANGEROUS.matcher(value).find();
    }
}
