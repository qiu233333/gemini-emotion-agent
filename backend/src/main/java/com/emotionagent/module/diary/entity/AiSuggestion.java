package com.emotionagent.module.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_suggestion")
public class AiSuggestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long diaryId;
    private Long userId;
    private Integer contentVersion;
    private Long analysisId;
    private String requestId;
    private Boolean usedHistory;
    private String referencedDiaryIds; // JSON array string
    private String modelName;
    private String promptVersion;
    private String status;
    private String empathyText;
    private String actionItems;        // JSON array string
    private String disclaimer;
    private Boolean validationPassed;
    private String validationNotes;
    private String agentTrace;         // JSON array string
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Boolean isLatest;
    private LocalDateTime createdAt;
}
