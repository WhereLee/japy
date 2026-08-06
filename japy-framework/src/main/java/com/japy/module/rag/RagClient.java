package com.japy.module.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * RAG 服务客户端：HTTP 调用 Python rag 服务（:8000）。
 * 降级：Python 未启动/超时 → 抛 RagUnavailableException，业务层处理为友好提示。
 */
@Slf4j
@Component
public class RagClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();

    @Value("${rag.service-url:http://127.0.0.1:8000}")
    private String baseUrl;

    /** 是否可达（探活） */
    public boolean available() {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(baseUrl + "/api/rag/health", String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    /** 触发同步（单本或全量） */
    public Map<String, Object> sync(Long novelId) {
        Map<String, Object> body = novelId == null ? Map.of() : Map.of("novel_id", novelId);
        return post("/api/rag/sync", body);
    }

    /** 问答 */
    public RagAnswer ask(Long novelId, String question) {
        Map<String, Object> body = Map.of("novel_id", novelId, "question", question);
        Map<String, Object> resp = post("/api/rag/ask", body);
        return RagAnswer.from(resp, om);
    }

    /** 索引状态 */
    public Map<String, Object> status(Long novelId) {
        String url = baseUrl + "/api/rag/status" + (novelId == null ? "" : "?novel_id=" + novelId);
        return get(url);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(om.writeValueAsString(body), headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(baseUrl + path, entity, String.class);
            JsonNode node = om.readTree(resp.getBody());
            if (node.path("code").asInt() != 200) {
                throw new RagUnavailableException(node.path("detail").asText("RAG 服务返回错误"));
            }
            return om.convertValue(node.path("data"), Map.class);
        } catch (RagUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("RAG 服务调用失败 {}: {}", path, e.getMessage());
            throw new RagUnavailableException("AI 问答服务未启动或不可用");
        }
    }

    private Map<String, Object> get(String url) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            JsonNode node = om.readTree(resp.getBody());
            return om.convertValue(node.path("data"), Map.class);
        } catch (Exception e) {
            log.warn("RAG 状态查询失败: {}", e.getMessage());
            throw new RagUnavailableException("AI 问答服务未启动或不可用");
        }
    }

    /** RAG 回答结构 */
    @lombok.Data
    public static class RagAnswer {
        private String answer;
        private List<Map<String, Object>> sources;
        private Map<String, Object> meta;

        @SuppressWarnings("unchecked")
        static RagAnswer from(Map<String, Object> data, ObjectMapper om) {
            RagAnswer a = new RagAnswer();
            a.answer = (String) data.getOrDefault("answer", "");
            a.sources = (List<Map<String, Object>>) data.getOrDefault("sources", List.of());
            a.meta = (Map<String, Object>) data.get("meta");
            return a;
        }
    }
}
