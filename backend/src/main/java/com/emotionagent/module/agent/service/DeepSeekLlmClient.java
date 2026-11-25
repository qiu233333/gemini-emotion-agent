package com.emotionagent.module.agent.service;

import com.emotionagent.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek (OpenAI 兼容协议) 大模型 HTTP 适配器。
 * 密钥仅保存在服务端环境变量，支持超时控制、有限重试与 Token/耗时采集。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekLlmClient {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class LlmCallResult {
        private boolean success;
        private String content;
        private String modelName;
        private int latencyMs;
        private int promptTokens;
        private int completionTokens;
        private String errorMessage;
    }

    public boolean isLiveConfigured() {
        String key = appProperties.getLlm().getApiKey();
        return key != null && !key.isBlank() && !key.startsWith("your-");
    }

    public LlmCallResult chat(List<Map<String, String>> messages, boolean jsonMode) {
        if (!isLiveConfigured()) {
            return LlmCallResult.builder()
                    .success(false)
                    .modelName("rule-fallback")
                    .errorMessage("DEEPSEEK_API_KEY 未配置，使用本地受控降级模式")
                    .build();
        }

        AppProperties.Llm cfg = appProperties.getLlm();
        int maxAttempts = Math.max(1, cfg.getMaxRetries() + 1);
        long start = System.currentTimeMillis();
        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(Math.min(10_000, cfg.getTimeoutSeconds() * 1000));
                factory.setReadTimeout(cfg.getTimeoutSeconds() * 1000);

                RestClient client = RestClient.builder()
                        .baseUrl(cfg.getBaseUrl())
                        .requestFactory(factory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.getApiKey())
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();

                Map<String, Object> body = new HashMap<>();
                body.put("model", cfg.getModel());
                body.put("messages", messages);
                body.put("temperature", cfg.getTemperature());
                body.put("max_tokens", cfg.getMaxTokens());
                if (jsonMode) {
                    body.put("response_format", Map.of("type", "json_object"));
                }

                String raw = client.post()
                        .uri("/v1/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(String.class);

                JsonNode root = objectMapper.readTree(raw);
                String text = root.path("choices").path(0).path("message").path("content").asText("");
                int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
                int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
                String modelUsed = root.path("model").asText(cfg.getModel());

                if (!text.isBlank()) {
                    return LlmCallResult.builder()
                            .success(true)
                            .content(text.trim())
                            .modelName(modelUsed)
                            .latencyMs((int) (System.currentTimeMillis() - start))
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .build();
                }
                lastError = "模型返回内容为空";
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("DeepSeek call attempt {}/{} failed: {}", attempt, maxAttempts, lastError);
            }
        }

        return LlmCallResult.builder()
                .success(false)
                .modelName(cfg.getModel())
                .latencyMs((int) (System.currentTimeMillis() - start))
                .errorMessage(lastError)
                .build();
    }
}
