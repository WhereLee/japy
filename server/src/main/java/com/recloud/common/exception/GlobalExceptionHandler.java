package com.recloud.common.exception;

import com.recloud.common.result.R;
import com.recloud.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 分层处理：
 * 1. BizException → 业务错误码，HTTP 400（可预知的业务错误，WARN 级别）
 * 2. MethodArgumentNotValidException → 参数校验失败，HTTP 400
 * 3. ConstraintViolationException → 路径变量/查询参数校验失败，HTTP 400
 * 4. HttpMessageNotReadableException → 请求体格式错误，HTTP 400
 * 5. MissingServletRequestParameterException → 缺少必要参数，HTTP 400
 * 6. TypeMismatchException → 参数类型不匹配，HTTP 400
 * 7. HttpRequestMethodNotSupportedException → 请求方法不支持，HTTP 405
 * 8. NoHandlerFoundException / NoResourceFoundException → 404
 * 9. AccessDeniedException → 权限不足，HTTP 403
 * 10. Exception → 兜底，HTTP 500（未知异常，ERROR 级别，不暴露堆栈）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 → 返回业务错误码
     */
    @ExceptionHandler(BizException.class)
    public R<?> handleBizException(BizException e, HttpServletResponse response) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        int code = e.getCode();
        // HTTP 状态码映射
        switch (code) {
            case 401 -> response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            case 403 -> response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            case 404 -> response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            case 429 -> response.setStatus(429);
            default -> response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid @RequestBody）→ 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleValidationException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return R.fail(400, msg);
    }

    /**
     * 路径变量/查询参数校验异常（@Validated @PathVariable/@RequestParam）→ 400
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleConstraintViolation(jakarta.validation.ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", msg);
        return R.fail(400, msg);
    }

    /**
     * 请求体格式错误（JSON 解析失败）→ 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return R.fail(400, "请求体格式错误");
    }

    /**
     * 缺少必要请求参数 → 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return R.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 参数类型不匹配（如传 "abc" 给 Integer 参数）→ 400
     */
    @ExceptionHandler(TypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleTypeMismatch(TypeMismatchException e) {
        log.warn("参数类型不匹配: {}", e.getMessage());
        return R.fail(400, "参数类型不匹配: " + e.getPropertyName());
    }

    /**
     * 请求方法不支持（如 GET-only 接口收到 POST）→ 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return R.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 资源不存在 → 404
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<?> handleNotFound(Exception e) {
        log.warn("资源不存在: {}", e.getMessage());
        return R.fail(404, "资源不存在");
    }

    /**
     * 权限不足 → 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleAccessDeniedException(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 兜底：未知异常 → 500（不暴露堆栈）
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("未知异常", e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
