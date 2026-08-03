package com.japy.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japy.module.system.entity.SysOperLog;
import com.japy.module.system.mapper.SysOperLogMapper;
import com.japy.mq.MqService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作日志切面：@OperLog 注解方法 → 组装日志 → 发 RocketMQ 异步落库。
 * MQ 不可用时降级为同步落库（保证日志不丢）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final MqService mqService;
    private final SysOperLogMapper operLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        String error = null;
        Object result;
        try {
            result = pjp.proceed();
            if (result instanceof com.japy.common.R<?> r && r.getCode() != 200) {
                error = r.getMsg();
            }
            return result;
        } catch (Throwable t) {
            error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            throw t;
        } finally {
            try {
                SysOperLog entry = build(pjp, operLog, System.currentTimeMillis() - start, error);
                if (!mqService.send("japy_oper_log", "oper", objectMapper.writeValueAsString(entry))) {
                    // 降级：同步落库
                    operLogMapper.insert(entry);
                }
            } catch (Exception e) {
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
        }
    }

    private SysOperLog build(ProceedingJoinPoint pjp, OperLog operLog, long cost, String error) {
        SysOperLog entry = new SysOperLog();
        entry.setTitle(operLog.title());
        entry.setBusinessType(operLog.businessType());
        entry.setMethod(pjp.getSignature().toShortString());
        entry.setStatus(error == null ? 0 : 1);
        entry.setErrorMsg(error);
        entry.setCostTime(cost);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        entry.setOperName(auth != null && auth.getPrincipal() instanceof com.japy.security.LoginUser lu
                ? lu.getUser().getNickname() : "anonymous");

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            entry.setRequestMethod(request.getMethod());
            entry.setOperUrl(request.getRequestURI());
            entry.setOperIp(clientIp(request));
        }
        entry.setOperParam(buildParam(pjp.getArgs()));
        return entry;
    }

    private String buildParam(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                if (arg instanceof MultipartFile f) {
                    sb.append("[file:").append(f.getOriginalFilename()).append("], ");
                } else {
                    String json = objectMapper.writeValueAsString(arg);
                    sb.append(json.length() > 200 ? json.substring(0, 200) + "…" : json).append(", ");
                }
            } catch (Exception ignored) {
                sb.append("[unserializable], ");
            }
        }
        String s = sb.toString();
        return s.isEmpty() ? "(无参数)" : s.substring(0, s.length() - 2);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        return ip != null && !ip.isBlank() ? ip.split(",")[0].trim() : request.getRemoteAddr();
    }
}
