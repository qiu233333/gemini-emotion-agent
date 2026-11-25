package com.emotionagent;

import com.emotionagent.module.agent.service.OutputSafetyValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OutputSafetyValidatorTest {

    private final OutputSafetyValidator validator = new OutputSafetyValidator();

    @Test
    void testValidOutputAndReferenceSanitization() {
        var res = validator.validateSuggestion(
                "听起来你最近为了答辩准备很辛苦，能坚持一步步推进已经很不容易了。",
                List.of("把明天的修改清单拆成两小项", "今晚提前半小时休息"),
                List.of(10L, 999L), // 999L 为模型幻觉编造的 ID，应被过滤
                Set.of(10L, 11L)
        );
        assertTrue(res.isPassed());
        assertEquals(List.of(10L), res.getSanitizedReferencedIds());
    }

    @Test
    void testRejectClinicalDiagnosisTerms() {
        var res = validator.validateSuggestion(
                "根据你的表现，你已经确诊中度抑郁，建议立刻服药治疗。",
                List.of("自行购买抗抑郁药"),
                List.of(),
                Set.of()
        );
        assertFalse(res.isPassed());
        assertTrue(res.getIssues().stream().anyMatch(s -> s.contains("医疗诊断")));
    }
}
