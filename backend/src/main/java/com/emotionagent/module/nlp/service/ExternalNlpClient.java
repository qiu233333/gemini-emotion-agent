package com.emotionagent.module.nlp.service;

import com.emotionagent.config.AppProperties;
import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 外部 Python NLP 扩展服务适配器（支持 SnowNLP / TF-IDF+LR / BERT 对照分类）。
 * 当外部服务未开启或超时时自动降级回本地规则引擎 RULE_V1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalNlpClient {

    private final AppProperties appProperties;
    private final RuleEmotionAnalyzer ruleEmotionAnalyzer;

    public EmotionAnalysisResult analyzeWithMethod(String text, String method) {
        String baseUrl = appProperties.getNlp().getExternalServiceUrl();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(8000);
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .requestFactory(factory)
                    .build();

            EmotionAnalysisResult resp = client.post()
                    .uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text, "method", method))
                    .retrieve()
                    .body(EmotionAnalysisResult.class);
            if (resp != null && resp.getPrimaryEmotion() != null) {
                return resp;
            }
        } catch (Exception ex) {
            log.warn("External NLP service ({}) unavailable for method={}, fallback to RULE_V1: {}",
                    baseUrl, method, ex.getMessage());
        }
        EmotionAnalysisResult fallback = ruleEmotionAnalyzer.analyze(text);
        fallback.setExplanation("[外部模型未连接，已自动降级至规则引擎] " + fallback.getExplanation());
        return fallback;
    }
}
