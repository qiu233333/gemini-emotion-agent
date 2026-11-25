package com.emotionagent.module.nlp.service;

import com.emotionagent.config.AppProperties;
import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmotionAnalyzerRouter {

    private final RuleEmotionAnalyzer ruleEmotionAnalyzer;
    private final ExternalNlpClient externalNlpClient;
    private final AppProperties appProperties;

    public EmotionAnalysisResult analyze(String text, String requestedMethod) {
        String method = (requestedMethod == null || requestedMethod.isBlank())
                ? appProperties.getNlp().getDefaultMethod()
                : requestedMethod.trim().toUpperCase();

        if ("RULE_V1".equals(method)) {
            return ruleEmotionAnalyzer.analyze(text);
        }
        return externalNlpClient.analyzeWithMethod(text, method);
    }
}
