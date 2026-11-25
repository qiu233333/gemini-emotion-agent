package com.emotionagent.module.nlp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionAnalysisResult {
    private String analyzerMethod;          // RULE_V1 / SNOWNLP / ML_TFIDF / BERT
    private String polarity;                // POSITIVE / NEGATIVE / NEUTRAL / MIXED / UNCERTAIN
    private String primaryEmotion;          // JOY / ANXIETY / SADNESS / ANGER / CALM / MIXED / UNCERTAIN
    private List<String> secondaryEmotions;
    private Map<String, Double> emotionScores;
    private int intensityScore;             // 0-100 非医学辅助强度分
    private List<String> keywords;
    private String explanation;
}
