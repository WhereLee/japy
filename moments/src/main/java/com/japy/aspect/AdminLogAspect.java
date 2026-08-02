package com.japy.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.common.AdminLog;
import com.japy.common.UserContext;
import com.japy.entity.OperationLog;
import com.japy.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端操作日志切面：拦截标注 @AdminLog 的接口，
 * 自动记录 操作人 / 请求路径 / 参数摘要 / 耗时 / 成功或失败原因，落 operation_log 表。
 *
 * 设计：
 * - 环绕通知：无论成功还是抛异常都记录（异常信息存 error 列）
 * - 日志落库失败绝不影响主流程（吞掉并打 warn）
 * - 参数摘要自动序列化，MultipartFile 只记文件名，超长截断
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AdminLogAspect {

    private final OperationLogMapper logMapper;
    private final ObjectMapper objectMapper;

    private static final int MAX_DETAIL = 500;

    @Around("@annotation(adminLog)")
    public Object around(ProceedingJoinPoint pjp, AdminLog adminLog) throws Throwable {
        long start = System.currentTimeMillis();
        String error = null;
        Object result;
        try {
            result = pjp.proceed();
            // 业务失败（R.code != 200）同样记为失败，如"用户不存在"等
            if (result instanceof com.japy.common.R<?> r && r.getCode() != 200) {
                error = r.getMsg();
            }
            return result;
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            throw t;
        } finally {
            try {
                record(pjp, adminLog, System.currentTimeMillis() - start, error);
            } catch (Exception e) {
                // 日志记录失败不影响主流程
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
    }

    private void record(ProceedingJoinPoint pjp, AdminLog adminLog, long costMs, String error) {
        OperationLog logEntry = new OperationLog();
        logEntry.setAdminId(UserContext.getUserId());
        logEntry.setAction(adminLog.action());
        logEntry.setCostMs((int) costMs);

        // 请求信息（真实路径，如 POST /api/admin/users/5/ban）
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            logEntry.setMethod(request.getMethod() + " " + request.getRequestURI());
            logEntry.setIp(clientIp(request));
        }

        // 参数摘要
        logEntry.setDetail(buildDetail(pjp.getArgs()));
        // 错误信息
        if (error != null) {
            logEntry.setError(truncate(error, MAX_DETAIL));
        }
        logMapper.insert(logEntry);
    }

    /** 参数摘要：body/路径参数 JSON 化，MultipartFile 只记文件名，超长截断 */
    private String buildDetail(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                if (arg instanceof MultipartFile file) {
                    sb.append("[file: ").append(file.getOriginalFilename())
                            .append(" (").append(file.getSize()).append("B)]");
                } else if (arg instanceof org.springframework.web.multipart.MultipartFile[]) {
                    sb.append("[files]");
                } else {
                    String json = objectMapper.writeValueAsString(arg);
                    sb.append(truncate(json, 200));
                }
            } catch (Exception e) {
                sb.append("[unserializable]");
            }
            sb.append(", ");
        }
        String detail = sb.toString();
        return truncate(detail.isEmpty() ? "(无参数)" : detail.substring(0, detail.length() - 2), MAX_DETAIL);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].strip();
        }
        return request.getRemoteAddr();
    }
}
