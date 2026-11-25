package com.emotionagent.module.nlp.service;

import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;

public interface EmotionAnalyzer {
    String methodName();
    EmotionAnalysisResult analyze(String text);
}
