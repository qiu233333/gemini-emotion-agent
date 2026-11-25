package com.emotionagent.module.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("emotion_analysis")
public class EmotionAnalysis {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long diaryId;
    private Long userId;
    private Integer contentVersion;
    private String analyzerMethod;
    private String polarity;
    private String primaryEmotion;
    private String secondaryEmotions; // JSON array string
    private String emotionScores;     // JSON object string
    private Integer intensityScore;
    private String keywords;          // JSON array string
    private String riskLevel;
    private String riskMatchedRules;  // JSON array string
    private String explanation;
    private Boolean isLatest;
    private LocalDateTime analyzedAt;
}
