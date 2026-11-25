package com.emotionagent;

import com.emotionagent.module.nlp.dto.EmotionAnalysisResult;
import com.emotionagent.module.nlp.service.RuleEmotionAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleEmotionAnalyzerTest {

    private final RuleEmotionAnalyzer analyzer = new RuleEmotionAnalyzer();

    @Test
    void testPositiveJoy() {
        EmotionAnalysisResult r = analyzer.analyze("今天拿到心仪公司的offer，真的太开心太兴奋了，非常有成就感！");
        assertEquals("POSITIVE", r.getPolarity());
        assertEquals("JOY", r.getPrimaryEmotion());
        assertTrue(r.getIntensityScore() >= 55);
        assertFalse(r.getKeywords().isEmpty());
    }

    @Test
    void testNegationExpression() {
        // 专项难例：“并不开心”
        EmotionAnalysisResult r = analyzer.analyze("虽然考完了试，但我今天回来之后其实并不开心，感觉挺失落的。");
        assertEquals("NEGATIVE", r.getPolarity());
        assertEquals("SADNESS", r.getPrimaryEmotion());
    }

    @Test
    void testAdversativeMixedEmotion() {
        // 专项难例：“有点担心但仍然期待”
        EmotionAnalysisResult r = analyzer.analyze("临近毕业答辩有点担心自己讲不好，但是看到系统已经成形仍然非常期待展示成果。");
        assertEquals("MIXED", r.getPolarity());
        assertFalse(r.getSecondaryEmotions().isEmpty());
    }

    @Test
    void testAnxietyWithCampusContext() {
        EmotionAnalysisResult r = analyzer.analyze("这周三个ddl堆在一起，晚上焦虑到失眠，感觉压力山大。");
        assertEquals("NEGATIVE", r.getPolarity());
        assertEquals("ANXIETY", r.getPrimaryEmotion());
    }

    @Test
    void testUncertainFactualText() {
        EmotionAnalysisResult r = analyzer.analyze("上午去教学楼上了两节课，中午在二食堂吃了拉面，下午回宿舍洗衣服。");
        assertEquals("UNCERTAIN", r.getPolarity());
        assertEquals("UNCERTAIN", r.getPrimaryEmotion());
    }
}
