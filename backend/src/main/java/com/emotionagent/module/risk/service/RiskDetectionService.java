package com.emotionagent.module.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.module.risk.entity.RiskRule;
import com.emotionagent.module.risk.mapper.RiskRuleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskDetectionService {

    private final RiskRuleMapper riskRuleMapper;

    private static final Pattern BUILTIN_CRISIS_PATTERN = Pattern.compile(
            "自杀|轻生|不想活|结束生命|割腕|跳楼|自残|伤害自己|活不下去|想彻底消失|不如死了"
    );

    private static final String DEFAULT_HIGH_RISK_TEMPLATE =
            "我注意到你此刻正经历非常沉重的痛苦。请先停下来照顾好自己，你不必独自承受这一切。"
                    + "建议立即联系身边信任的家人、朋友或辅导员，也可以拨打全国统一心理援助热线 12356、联系学校心理健康中心，"
                    + "如遇紧急危险请直接拨打 120 或 110。本系统仅用于日常情绪记录，无法替代专业危机支持。";

    @Data
    @AllArgsConstructor
    public static class RiskResult {
        private String level; // NONE / LOW / HIGH
        private List<String> matchedRules;
        private String safeTemplate;
    }

    public RiskResult detect(String text) {
        if (text == null || text.isBlank()) {
            return new RiskResult("NONE", List.of(), null);
        }

        List<String> matched = new ArrayList<>();
        String highestLevel = "NONE";
        String selectedTemplate = null;

        List<RiskRule> rules = riskRuleMapper.selectList(
                new LambdaQueryWrapper<RiskRule>().eq(RiskRule::getEnabled, true)
        );
        for (RiskRule rule : rules) {
            if (isMatched(text, rule)) {
                matched.add(rule.getName());
                if ("HIGH".equalsIgnoreCase(rule.getSeverity())) {
                    highestLevel = "HIGH";
                    if (selectedTemplate == null || !selectedTemplate.isBlank()) {
                        selectedTemplate = rule.getSafeTemplate();
                    }
                } else if ("NONE".equals(highestLevel)) {
                    highestLevel = "LOW";
                    if (selectedTemplate == null) {
                        selectedTemplate = rule.getSafeTemplate();
                    }
                }
            }
        }

        // 内置危机安全网：即使数据库规则被误关闭，极端危机表述仍会触发高风险保护
        if (!"HIGH".equals(highestLevel) && BUILTIN_CRISIS_PATTERN.matcher(text).find()) {
            highestLevel = "HIGH";
            matched.add("内置危机兜底规则");
            selectedTemplate = DEFAULT_HIGH_RISK_TEMPLATE;
        }

        if ("HIGH".equals(highestLevel) && (selectedTemplate == null || selectedTemplate.isBlank())) {
            selectedTemplate = DEFAULT_HIGH_RISK_TEMPLATE;
        }

        return new RiskResult(highestLevel, matched, selectedTemplate);
    }

    private boolean isMatched(String text, RiskRule rule) {
        try {
            if ("REGEX".equalsIgnoreCase(rule.getMatchType())) {
                return Pattern.compile(rule.getPattern()).matcher(text).find();
            }
            for (String kw : rule.getPattern().split("[,，|]")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty() && text.contains(trimmed)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("Invalid risk rule id={} pattern={}: {}", rule.getId(), rule.getPattern(), ex.getMessage());
            return false;
        }
    }
}
