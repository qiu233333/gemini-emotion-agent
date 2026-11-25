package com.emotionagent.module.nlp.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;
import com.emotionagent.module.nlp.service.EmotionAnalyzerRouter;
import com.emotionagent.module.risk.service.RiskDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 情感分析即时预览与多算法对比接口（支持规则法 RULE_V1 与 Python 扩展服务 SNOWNLP / ML_TFIDF / BERT 对比）。
 */
@RestController
@RequestMapping("/api/nlp")
@RequiredArgsConstructor
public class NlpExperimentController {

    private final EmotionAnalyzerRouter analyzerRouter;
    private final RiskDetectionService riskDetectionService;

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(@RequestBody Map<String, String> body) {
        String text = body == null ? "" : body.getOrDefault("text", "");
        String method = body == null ? "RULE_V1" : body.getOrDefault("method", "RULE_V1");
        EmotionAnalysisResult result = analyzerRouter.analyze(text, method);
        RiskDetectionService.RiskResult risk = riskDetectionService.detect(text);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("analysis", result);
        resp.put("risk", risk);
        return ApiResponse.ok(resp);
    }
}
