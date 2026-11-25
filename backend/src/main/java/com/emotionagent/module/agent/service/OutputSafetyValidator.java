package com.emotionagent.module.agent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 大模型输出安全与边界校验器：
 * 1) 校验非医疗边界（禁止医学诊断、处方建议、临床症状定性）
 * 2) 校验危险表述与绝对化人格推断
 * 3) 校验引用的日记编号均来自本次真实检索结果，防止幻觉引用
 */
@Service
public class OutputSafetyValidator {

    private static final Pattern CLINICAL_FORBIDDEN = Pattern.compile(
            "确诊|临床诊断|中度抑郁|重度抑郁|双相情感障碍|精神分裂|抗抑郁药|处方药|建议服药|服用.*药|心理疾病患者|你患有|治愈抑郁|替代医生|不用看医生"
    );

    private static final Pattern HARMFUL_FORBIDDEN = Pattern.compile(
            "自杀方法|如何割腕|怎么结束生命|去死吧|伤害他人"
    );

    private static final Pattern OVERGENERALIZATION = Pattern.compile(
            "你是一个天生脆弱|你注定失败|你有人格障碍|你一直都很失败"
    );

    @Data
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean passed;
        private List<String> issues;
        private List<Long> sanitizedReferencedIds;
    }

    public ValidationResult validateSuggestion(String empathyText,
                                               List<String> actions,
                                               List<Long> claimedRefIds,
                                               Set<Long> allowedRefIds) {
        List<String> issues = new ArrayList<>();

        if (empathyText == null || empathyText.isBlank() || empathyText.length() < 8) {
            issues.add("共情回应内容过短或为空");
        }
        if (actions == null || actions.isEmpty() || actions.size() > 4) {
            issues.add("行动建议数量不在 1-3 条范围内");
        }

        StringBuilder full = new StringBuilder();
        if (empathyText != null) full.append(empathyText).append("\n");
        if (actions != null) {
            for (String a : actions) {
                if (a != null) full.append(a).append("\n");
            }
        }
        String combined = full.toString();

        if (CLINICAL_FORBIDDEN.matcher(combined).find()) {
            issues.add("包含非允许的医疗诊断或用药措辞");
        }
        if (HARMFUL_FORBIDDEN.matcher(combined).find()) {
            issues.add("包含危险诱导内容");
        }
        if (OVERGENERALIZATION.matcher(combined).find()) {
            issues.add("包含无依据的绝对化人格推断");
        }

        // 核验引用的日记编号，剔除模型臆造的 ID
        List<Long> validRefs = new ArrayList<>();
        if (claimedRefIds != null && allowedRefIds != null) {
            for (Long id : claimedRefIds) {
                if (id != null && allowedRefIds.contains(id) && !validRefs.contains(id)) {
                    validRefs.add(id);
                }
            }
        }

        return new ValidationResult(issues.isEmpty(), issues, validRefs);
    }

    public ValidationResult validateChatReply(String replyText) {
        List<String> issues = new ArrayList<>();
        if (replyText == null || replyText.isBlank()) {
            issues.add("回复为空");
        } else {
            if (CLINICAL_FORBIDDEN.matcher(replyText).find()) {
                issues.add("回复触发医疗诊断边界词");
            }
            if (HARMFUL_FORBIDDEN.matcher(replyText).find()) {
                issues.add("回复触发安全禁词");
            }
        }
        return new ValidationResult(issues.isEmpty(), issues, List.of());
    }
}
