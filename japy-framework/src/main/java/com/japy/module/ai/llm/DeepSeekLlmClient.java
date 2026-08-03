package com.japy.module.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM 客户端（OpenAI 兼容 /chat/completions，手写实现，无框架黑盒）。
 * 密钥来自 ai.llm.api-key：本地 application-local.yml（gitignore），生产环境变量 DEEPSEEK_API_KEY。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekLlmClient implements LlmClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${ai.llm.api-key:}")
    private String apiKey;
    @Value("${ai.llm.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;
    @Value("${ai.llm.model:deepseek-v4-flash}")
    private String model;

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(String system, String user) {
        if (!available()) {
            throw new LlmUnavailableException("LLM API key 未配置（ai.llm.api-key），已降级为纯规则模式");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3);
        try {
            JsonNode resp = restClientBuilder.build()
                    .post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = resp.path("choices").get(0).path("message").path("content").asText();
            int tokenIn = resp.path("usage").path("prompt_tokens").asInt(0);
            int tokenOut = resp.path("usage").path("completion_tokens").asInt(0);
            return new LlmResponse(content, tokenIn, tokenOut);
        } catch (Exception e) {
            log.warn("LLM 调用失败: {}", e.getMessage());
            throw new LlmUnavailableException("LLM 调用失败: " + e.getMessage());
        }
    }
}
