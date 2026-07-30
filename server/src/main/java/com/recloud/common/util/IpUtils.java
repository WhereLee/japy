package com.recloud.common.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP 地址工具类
 * <p>
 * 统一从 HttpServletRequest 中获取客户端真实 IP，
 * 兼容反向代理（X-Forwarded-For / X-Real-IP）场景。
 */
public final class IpUtils {

    private IpUtils() {}

    /**
     * 从当前请求上下文获取客户端 IP
     */
    public static String getClientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        return getClientIp(attrs.getRequest());
    }

    /**
     * 从 HttpServletRequest 获取客户端 IP
     * <p>
     * 优先级：X-Forwarded-For → X-Real-IP → remoteAddr
     * 多级代理时取第一个 IP（真实客户端）
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        // IPv6 本地地址转 IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }
}
