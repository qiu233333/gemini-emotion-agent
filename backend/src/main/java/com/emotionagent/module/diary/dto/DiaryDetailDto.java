package com.emotionagent.module.diary.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DiaryDetailDto {
    private Long id;
    private String title;
    private String content;
    private LocalDate entryDate;
    private String selfEmotionTag;
    private String correctedEmotionTag;
    private String correctedPolarity;
    private String effectiveEmotion;   // 纠偏优先 -> 分析结果 -> 自评
    private Integer contentVersion;
    private Boolean aiConsent;
    private Boolean memoryIncluded;
    private Boolean analysisStale;     // 文本修改后旧分析是否已过期
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private AnalysisView analysis;
    private SuggestionView suggestion;

    @Data
    @Builder
    public static class AnalysisView {
        private Long id;
        private Integer contentVersion;
        private String analyzerMethod;
        private String polarity;
        private String primaryEmotion;
        private List<String> secondaryEmotions;
        private Map<String, Double> emotionScores;
        private Integer intensityScore;
        private List<String> keywords;
        private String riskLevel;
        private List<String> riskMatchedRules;
        private String explanation;
        private LocalDateTime analyzedAt;
    }

    @Data
    @Builder
    public static class SuggestionView {
        private Long id;
        private Integer contentVersion;
        private String requestId;
        private Boolean usedHistory;
        private List<Long> referencedDiaryIds;
        private List<ReferencedDiaryBrief> referencedDiaries;
        private String modelName;
        private String promptVersion;
        private String status;
        private String empathyText;
        private List<String> actionItems;
        private String disclaimer;
        private Boolean validationPassed;
        private String validationNotes;
        private List<Map<String, Object>> agentTrace;
        private Integer latencyMs;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class ReferencedDiaryBrief {
        private Long id;
        private String title;
        private LocalDate entryDate;
    }
}
