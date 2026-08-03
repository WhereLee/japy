package com.japy.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * XSS 请求包装器：对 query 参数与 header 中的危险片段做 HTML 实体转义
 * （JSON body 由 @Xss 注解在参数绑定层拒绝，双路径覆盖）
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = clean(values[i]);
        }
        return cleaned;
    }

    @Override
    public String getHeader(String name) {
        return clean(super.getHeader(name));
    }

    /** 危险片段转义为无害字符（保留原语义，仅不可执行） */
    private static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replaceAll("(?i)<script", "&lt;script")
                .replaceAll("(?i)</script", "&lt;/script")
                .replaceAll("(?i)<iframe", "&lt;iframe")
                .replaceAll("(?i)javascript:", "javascript：")
                .replaceAll("(?i)on(load|error|click|mouseover|focus)\\s*=", "被过滤事件=")
                .replaceAll("(?i)alert\\s*\\(", "alert（");
    }
}
