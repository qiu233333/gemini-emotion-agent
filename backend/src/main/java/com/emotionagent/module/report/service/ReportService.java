package com.emotionagent.module.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.common.BusinessException;
import com.emotionagent.module.agent.service.DeepSeekLlmClient;
import com.emotionagent.module.agent.service.EmotionAgentOrchestrator;
import com.emotionagent.module.audit.service.AuditService;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.emotionagent.module.report.entity.PeriodicReport;
import com.emotionagent.module.report.mapper.PeriodicReportMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final PeriodicReportMapper reportMapper;
    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAnalysisMapper analysisMapper;
    private final DeepSeekLlmClient deepSeekLlmClient;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> listReports(Long userId) {
        List<PeriodicReport> list = reportMapper.selectList(new LambdaQueryWrapper<PeriodicReport>()
                .eq(PeriodicReport::getUserId, userId)
                .orderByDesc(PeriodicReport::getCreatedAt, PeriodicReport::getId));
        return list.stream().map(this::toView).toList();
    }

    public Map<String, Object> generateReport(Long userId, String reportType, String ip) {
        String type = "MONTHLY".equalsIgnoreCase(reportType) ? "MONTHLY" : "WEEKLY";
        LocalDate end = LocalDate.now();
        LocalDate start = "MONTHLY".equals(type) ? end.minusDays(29) : end.minusDays(6);

        List<EmotionDiary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .ge(EmotionDiary::getEntryDate, start)
                .le(EmotionDiary::getEntryDate, end)
                .orderByAsc(EmotionDiary::getEntryDate, EmotionDiary::getId));

        if (diaries.isEmpty()) {
            throw new BusinessException("该周期内暂无情绪日记，请先记录至少 1 篇日记后再生成周期报告");
        }

        List<Long> diaryIds = diaries.stream().map(EmotionDiary::getId).toList();
        Map<Long, EmotionAnalysis> anaMap = analysisMapper.selectList(
                new LambdaQueryWrapper<EmotionAnalysis>()
                        .in(EmotionAnalysis::getDiaryId, diaryIds)
                        .eq(EmotionAnalysis::getIsLatest, true))
                .stream().collect(Collectors.toMap(EmotionAnalysis::getDiaryId, a -> a, (a, b) -> b));

        Map<String, Integer> emoCounts = new LinkedHashMap<>();
        Map<String, Integer> kwCounts = new HashMap<>();
        double sumIntensity = 0;
        int intCnt = 0;
        StringBuilder diaryBriefs = new StringBuilder();

        for (EmotionDiary d : diaries) {
            EmotionAnalysis a = anaMap.get(d.getId());
            String emo = d.getCorrectedEmotionTag() != null ? d.getCorrectedEmotionTag()
                    : (a != null ? a.getPrimaryEmotion() : "UNCERTAIN");
            emoCounts.merge(emo, 1, Integer::sum);
            if (a != null && a.getIntensityScore() != null) {
                sumIntensity += a.getIntensityScore();
                intCnt++;
            }
            if (a != null && a.getKeywords() != null) {
                for (String kw : parseJson(a.getKeywords(), new TypeReference<List<String>>() {}, List.of())) {
                    kwCounts.merge(kw, 1, Integer::sum);
                }
            }
            String snippet = d.getContent().length() > 60 ? d.getContent().substring(0, 60) + "..." : d.getContent();
            diaryBriefs.append(String.format("- [日记#%d | %s | 情绪:%s] %s：%s\n",
                    d.getId(), d.getEntryDate(), EmotionAgentOrchestrator.emotionCn(emo), d.getTitle(), snippet));
        }

        List<String> topKeywords = kwCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(6)
                .map(Map.Entry::getKey)
                .toList();

        double avgIntensity = intCnt > 0 ? Math.round((sumIntensity / intCnt) * 10.0) / 10.0 : 0.0;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("emotionCounts", emoCounts);
        snapshot.put("topKeywords", topKeywords);
        snapshot.put("avgIntensity", avgIntensity);
        snapshot.put("recordedDays", diaries.stream().map(EmotionDiary::getEntryDate).distinct().count());

        String summaryText;
        List<String> highlights;
        List<String> suggestions;
        String modelName = "rule-fallback";
        String status = "FALLBACK";

        List<Map<String, String>> prompt = List.of(
                Map.of("role", "system", "content", """
                        你是大学生日常情绪回顾总结助手（非医疗定位）。请严格基于用户周期内的日记统计与摘要生成 JSON 总结，禁止医学诊断或编造事实。
                        返回严格 JSON：
                        {
                          "summary": "120-200字的周期整体情绪回顾，语气温暖客观",
                          "highlights": ["观察点1", "观察点2", "观察点3"],
                          "suggestions": ["下周期可行建议1", "下周期可行建议2", "下周期可行建议3"]
                        }
                        """),
                Map.of("role", "user", "content", String.format("""
                        周期类型：%s（%s 至 %s），共记录 %d 篇日记。
                        情绪分布：%s；高频关键词：%s；平均强度辅助分：%.1f。
                        日记摘要：
                        %s
                        """, "MONTHLY".equals(type) ? "月报" : "周报", start, end, diaries.size(),
                        emoCounts, topKeywords, avgIntensity, diaryBriefs))
        );

        DeepSeekLlmClient.LlmCallResult llmResp = deepSeekLlmClient.chat(prompt, true);
        if (llmResp.isSuccess()) {
            try {
                String cleaned = llmResp.getContent().trim()
                        .replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
                JsonNode root = objectMapper.readTree(cleaned);
                summaryText = root.path("summary").asText("").trim();
                highlights = extractStrList(root.path("highlights"));
                suggestions = extractStrList(root.path("suggestions"));
                if (!summaryText.isBlank() && !highlights.isEmpty()) {
                    modelName = llmResp.getModelName();
                    status = "SUCCESS";
                } else {
                    throw new IllegalStateException("incomplete report json");
                }
            } catch (Exception ex) {
                FallbackReport fb = buildFallbackReport(type, start, end, diaries.size(), emoCounts, topKeywords, avgIntensity);
                summaryText = fb.summary;
                highlights = fb.highlights;
                suggestions = fb.suggestions;
            }
        } else {
            FallbackReport fb = buildFallbackReport(type, start, end, diaries.size(), emoCounts, topKeywords, avgIntensity);
            summaryText = fb.summary;
            highlights = fb.highlights;
            suggestions = fb.suggestions;
        }

        PeriodicReport report = new PeriodicReport();
        report.setUserId(userId);
        report.setReportType(type);
        report.setStartDate(start);
        report.setEndDate(end);
        report.setDiaryCount(diaries.size());
        report.setStatsSnapshot(toJson(snapshot));
        report.setSummaryText(summaryText);
        report.setHighlights(toJson(highlights));
        report.setSuggestions(toJson(suggestions));
        report.setReferencedDiaryIds(toJson(diaryIds));
        report.setModelName(modelName);
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);

        auditService.record(userId, "USER", "GENERATE_REPORT", "REPORT", report.getId(),
                "type=" + type + ", diaries=" + diaries.size() + ", status=" + status, ip);

        return toView(report);
    }

    public void deleteReport(Long userId, Long reportId) {
        PeriodicReport r = reportMapper.selectById(reportId);
        if (r == null) throw new BusinessException(404, "报告不存在");
        if (!Objects.equals(r.getUserId(), userId)) throw new BusinessException(403, "无权删除他人报告");
        reportMapper.deleteById(reportId);
    }

    private FallbackReport buildFallbackReport(String type, LocalDate start, LocalDate end, int count,
                                               Map<String, Integer> emoCounts, List<String> topKws, double avgIntensity) {
        String dominant = emoCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> EmotionAgentOrchestrator.emotionCn(e.getKey()))
                .orElse("多元波动");
        String kwStr = topKws.isEmpty() ? "日常学习与生活" : String.join("、", topKws.subList(0, Math.min(4, topKws.size())));
        String label = "MONTHLY".equals(type) ? "近 30 天" : "近 7 天";

        String summary = String.format(
                "在 %s（%s 至 %s）期间，你一共留下了 %d 篇情绪记录，主要围绕「%s」展开。整体情绪以“%s”为主，情绪强度辅助均值为 %.1f/100。坚持记录让你更清楚地看见了压力来源与情绪恢复的节奏。",
                label, start, end, count, kwStr, dominant, avgIntensity);

        List<String> highlights = List.of(
                "记录活跃度：周期内共记录 " + count + " 篇，有助于形成稳定的自我觉察习惯。",
                "核心情绪特征：出现最多的情绪为「" + dominant + "」，高频关注词为「" + kwStr + "」。",
                "波动参考：非医学强度辅助均值为 " + avgIntensity + "，建议结合原始日记回看高波动日子的触发事件。"
        );
        List<String> suggestions = List.of(
                "回看本周期中让自己感到愉悦或平静的记录，提炼 1-2 个有效的自我恢复小习惯。",
                "对高频引发压力的主题提前拆解成可执行小任务，减少临近截止日期的集中消耗。",
                "下一周期继续保持轻松记录，无需追求每天写长文，几句话同样很有价值。"
        );
        return new FallbackReport(summary, highlights, suggestions);
    }

    private Map<String, Object> toView(PeriodicReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("reportType", r.getReportType());
        m.put("startDate", r.getStartDate());
        m.put("endDate", r.getEndDate());
        m.put("diaryCount", r.getDiaryCount());
        m.put("statsSnapshot", parseJson(r.getStatsSnapshot(), new TypeReference<Map<String, Object>>() {}, Map.of()));
        m.put("summaryText", r.getSummaryText());
        m.put("highlights", parseJson(r.getHighlights(), new TypeReference<List<String>>() {}, List.of()));
        m.put("suggestions", parseJson(r.getSuggestions(), new TypeReference<List<String>>() {}, List.of()));
        m.put("referencedDiaryIds", parseJson(r.getReferencedDiaryIds(), new TypeReference<List<Long>>() {}, List.of()));
        m.put("modelName", r.getModelName());
        m.put("status", r.getStatus());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private List<String> extractStrList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String t = n.asText("").trim();
                if (!t.isEmpty()) list.add(t);
            }
        }
        return list;
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "[]"; }
    }

    private <T> T parseJson(String raw, TypeReference<T> ref, T def) {
        if (raw == null || raw.isBlank()) return def;
        try { return objectMapper.readValue(raw, ref); } catch (Exception e) { return def; }
    }

    private record FallbackReport(String summary, List<String> highlights, List<String> suggestions) {}
}
