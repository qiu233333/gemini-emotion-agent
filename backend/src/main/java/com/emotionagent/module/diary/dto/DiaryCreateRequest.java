package com.emotionagent.module.diary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaryCreateRequest {
    @NotBlank(message = "日记标题不能为空")
    @Size(max = 120, message = "标题不能超过120字")
    private String title;

    @NotBlank(message = "日记正文不能为空")
    @Size(max = 5000, message = "正文不能超过5000字")
    private String content;

    @NotNull(message = "记录日期不能为空")
    private LocalDate entryDate;

    private String selfEmotionTag;
    private Boolean aiConsent = true;
    private Boolean memoryIncluded = true;
    private String requestId;
    private String analyzerMethod; // 可选: RULE_V1 / SNOWNLP / ML_TFIDF / BERT
    private Boolean useHistoryOverride; // 可选: 消融测试覆盖
}
