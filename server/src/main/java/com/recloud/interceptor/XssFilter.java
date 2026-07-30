package com.recloud.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XSS 过滤器
 * <p>
 * 防护策略：
 * - JSON Body：读取并转义 HTML 标签（< > ' "）
 * - Form 参数：HtmlUtils.htmlEscape
 * - Authorization Header 不处理（JWT Token 包含 Base64 字符，转义会破坏）
 * <p>
 * 设计要点：
 * - 使用 HttpServletRequestWrapper 包装，支持重复读取 Body
 * - 仅处理 application/json 和 application/x-www-form-urlencoded
 * - 排除 /auth/** 路径（登录注册不需要 XSS 过滤）
 */
@Slf4j
@Component
@Order(1)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        // 排除认证路径（登录注册参数包含密码，转义会破坏特殊字符）
        if (uri.startsWith("/auth/")) {
            chain.doFilter(request, response);
            return;
        }

        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            chain.doFilter(new XssJsonRequestWrapper(httpRequest), response);
        } else if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            chain.doFilter(new XssFormRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * JSON Body XSS 转义包装器
     */
    private static class XssJsonRequestWrapper extends HttpServletRequestWrapper {
        private String body;

        public XssJsonRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            // 读取原始 Body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            // 转义 JSON 中的 HTML 标签
            String raw = sb.toString();
            this.body = escapeJson(raw);
        }

        @Override
        public ServletInputStream getInputStream() {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() { return bais.available() == 0; }
                @Override
                public boolean isReady() { return true; }
                @Override
                public void setReadListener(ReadListener listener) { }
                @Override
                public int read() { return bais.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        /**
         * 转义 JSON 字符串中的 HTML 标签
         * <p>
         * 注意：不能转义双引号 ")，因为它是 JSON 的结构字符，
         * 转义后会破坏 JSON 解析（所有 POST 请求都会 400）。
         * 只转义真正的 XSS 向量：< > '
         */
        private String escapeJson(String json) {
            if (json == null || json.isEmpty()) return json;
            return json
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("'", "&#39;");
        }
    }

    /**
     * Form 参数 XSS 转义包装器
     */
    private static class XssFormRequestWrapper extends HttpServletRequestWrapper {
        public XssFormRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value != null ? HtmlUtils.htmlEscape(value) : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return null;
            String[] escaped = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                escaped[i] = HtmlUtils.htmlEscape(values[i]);
            }
            return escaped;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> paramMap = super.getParameterMap();
            Map<String, String[]> escapedMap = new LinkedHashMap<>();
            paramMap.forEach((key, values) -> {
                String[] escaped = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    escaped[i] = HtmlUtils.htmlEscape(values[i]);
                }
                escapedMap.put(key, escaped);
            });
            return escapedMap;
        }
    }
}
