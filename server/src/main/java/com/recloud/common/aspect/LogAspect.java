package com.recloud.common.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recloud.common.annotation.Log;
import com.recloud.common.entity.OperationLog;
import com.recloud.common.util.IpUtils;
import com.recloud.mq.OperationLogProducer;
import com.recloud.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 操作日志切面
 * <p>
 * 拦截 @Log 注解标注的方法，异步记录操作日志到数据库。
 * <p>
 * 设计要点：
 * - 异步写入（使用 logExecutor 线程池），不阻塞业务
 * - 通过 Executor.execute() 实现真正异步，避免 @Async 自调用失效问题
 * - 敏感字段脱敏（password/token/secret → ******）
 * - 请求参数截断 2000 字符，防止大参数撑爆数据库
 * - 记录执行耗时，便于性能分析
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final OperationLogProducer operationLogProducer;
    private final ObjectMapper objectMapper;

    /** 敏感字段名，匹配后脱敏 */
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>(
            Arrays.asList("password", "token", "secret", "accessToken", "refreshToken")
    );
    private static final int MAX_PARAMS_LENGTH = 2000;

    @Around("@annotation(com.recloud.common.annotation.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 先执行业务方法
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long executeTime = System.currentTimeMillis() - startTime;
            // 异步发送到 MQ（不阻塞主业务）
            try {
                OperationLog operationLog = buildOperationLog(joinPoint, result, error, executeTime);
                operationLogProducer.sendOperationLog(operationLog);
            } catch (Exception e) {
                log.warn("记录操作日志失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 构建操作日志对象
     */
    private OperationLog buildOperationLog(ProceedingJoinPoint joinPoint, Object result, Throwable error, long executeTime) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Log logAnnotation = method.getAnnotation(Log.class);

            OperationLog operationLog = new OperationLog();
            operationLog.setModule(logAnnotation.module());
            operationLog.setOperation(logAnnotation.operation());
            operationLog.setMethod(joinPoint.getTarget().getClass().getName() + "." + method.getName());
            operationLog.setRequestMethod(getHttpRequestMethod());
            operationLog.setRequestUrl(getRequestUrl());

            // 请求参数（脱敏 + 截断）
            if (logAnnotation.saveRequestData()) {
                String params = buildParams(joinPoint.getArgs());
                operationLog.setRequestParams(params);
            }

            // 响应结果
            if (logAnnotation.saveResponseData() && result != null) {
                String responseStr = objectMapper.writeValueAsString(result);
                if (responseStr.length() > MAX_PARAMS_LENGTH) {
                    responseStr = responseStr.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)";
                }
                operationLog.setResponseResult(responseStr);
            }

            // 状态
            operationLog.setStatus(error != null ? "FAIL" : "SUCCESS");
            if (error != null) {
                String errorMsg = error.getMessage();
                if (errorMsg != null && errorMsg.length() > 500) {
                    errorMsg = errorMsg.substring(0, 500);
                }
                operationLog.setErrorMessage(errorMsg);
            }

            operationLog.setExecuteTime(executeTime);

            // 操作人
            try {
                Long userId = SecurityUtils.getCurrentUserId();
                operationLog.setOperatorId(userId);
            } catch (Exception ignored) {
                // 未登录场景
            }

            // IP 和 UA
            HttpServletRequest request = getRequest();
            if (request != null) {
                operationLog.setIp(IpUtils.getClientIp(request));
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 500) {
                    userAgent = userAgent.substring(0, 500);
                }
                operationLog.setUserAgent(userAgent);
            }

            operationLog.setCreatedAt(LocalDateTime.now());

            return operationLog;
        } catch (Exception e) {
            log.error("构建操作日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建请求参数（JSON 级脱敏 + 截断）
     * <p>
     * 将每个参数转为 JSON 树后递归脱敏敏感字段，避免两类问题：
     * 1. DTO 对象中的敏感字段（如 password）被原样序列化进日志；
     * 2. 旧实现按“字符串包含敏感词”整体遮罩，会误伤含敏感词的普通文本。
     * 不可序列化的 Web 容器对象（Request/Response/MultipartFile 等）用类名占位。
     */
    private String buildParams(Object[] args) {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (Object arg : args) {
                if (arg == null) {
                    arrayNode.addNull();
                } else if (isNonSerializable(arg)) {
                    arrayNode.add(arg.getClass().getSimpleName());
                } else {
                    JsonNode node = objectMapper.valueToTree(arg);
                    maskSensitiveFields(node);
                    arrayNode.add(node);
                }
            }
            String params = objectMapper.writeValueAsString(arrayNode);
            if (params.length() > MAX_PARAMS_LENGTH) {
                params = params.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)";
            }
            return params;
        } catch (Exception e) {
            return "序列化失败: " + e.getMessage();
        }
    }

    /**
     * 递归脱敏：将对象节点中字段名命中敏感词的字符串值替换为 ******
     */
    private void maskSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> sensitiveNames = new ArrayList<>();
            obj.fieldNames().forEachRemaining(name -> {
                if (isSensitiveField(name)) {
                    sensitiveNames.add(name);
                }
            });
            for (String name : sensitiveNames) {
                obj.put(name, "******");
            }
            obj.fields().forEachRemaining(entry -> maskSensitiveFields(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::maskSensitiveFields);
        }
    }

    /**
     * 判断字段名是否为敏感字段（password/token/secret 等）
     */
    private boolean isSensitiveField(String name) {
        String lower = name.toLowerCase();
        for (String field : SENSITIVE_FIELDS) {
            if (lower.contains(field.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断参数是否为不可序列化的 Web 容器对象（直接序列化会失败或产生无意义内容）
     */
    private boolean isNonSerializable(Object arg) {
        return arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof org.springframework.web.multipart.MultipartFile
                || arg instanceof org.springframework.validation.BindingResult;
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getHttpRequestMethod() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getMethod() : "UNKNOWN";
    }

    private String getRequestUrl() {
        HttpServletRequest request = getRequest();
        return request != null ? request.getRequestURI() : "UNKNOWN";
    }
}
