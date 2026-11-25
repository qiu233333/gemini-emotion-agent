package com.emotionagent.module.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.config.AppProperties;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 受控历史记忆检索工具：仅按当前用户 ID、严格早于当前记录的时间窗口 (7-30 天)、
 * 且用户允许纳入记忆 (memoryIncluded=true) 的记录中筛选高相关摘要。
 */
@Service
@RequiredArgsConstructor
public class HistoryMemoryService {

    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAnalysisMapper analysisMapper;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Data
    @AllArgsConstructor
    public static class MemoryItem {
        private Long diaryId;
        private LocalDate entryDate;
        private String title;
        private String emotion;
        private List<String> keywords;
        private String snippet;
        private double relevanceScore;
    }

    public List<MemoryItem> retrieveRelevantHistory(Long userId,
                                                    Long excludeDiaryId,
                                                    LocalDate currentDate,
                                                    int windowDays,
                                                    String currentEmotion,
                                                    List<String> currentKeywords) {
        int clampedDays = Math.max(7, Math.min(30, windowDays));
        LocalDate anchor = currentDate != null ? currentDate : LocalDate.now();
        LocalDate start = anchor.minusDays(clampedDays);

        LambdaQueryWrapper<EmotionDiary> query = new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .eq(EmotionDiary::getMemoryIncluded, true)
                .ge(EmotionDiary::getEntryDate, start)
                .le(EmotionDiary::getEntryDate, anchor)
                .orderByDesc(EmotionDiary::getEntryDate, EmotionDiary::getId)
                .last("LIMIT 30");
        if (excludeDiaryId != null) {
            query.ne(EmotionDiary::getId, excludeDiaryId);
        }

        List<EmotionDiary> candidates = diaryMapper.selectList(query);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> ids = candidates.stream().map(EmotionDiary::getId).toList();
        Map<Long, EmotionAnalysis> analysisMap = analysisMapper.selectList(
                new LambdaQueryWrapper<EmotionAnalysis>()
                        .in(EmotionAnalysis::getDiaryId, ids)
                        .eq(EmotionAnalysis::getIsLatest, true)
        ).stream().collect(Collectors.toMap(EmotionAnalysis::getDiaryId, a -> a, (a, b) -> a));

        Set<String> curKwSet = currentKeywords == null ? Set.of() : new HashSet<>(currentKeywords);
        List<MemoryItem> scored = new ArrayList<>();

        for (EmotionDiary d : candidates) {
            // 仅使用严格早于当前记录的日记（若同日，只取 id 更小的先前记录）
            if (excludeDiaryId != null && d.getEntryDate().equals(anchor) && d.getId() >= excludeDiaryId) {
                continue;
            }
            EmotionAnalysis ana = analysisMap.get(d.getId());
            String emo = d.getCorrectedEmotionTag() != null ? d.getCorrectedEmotionTag()
                    : (ana != null ? ana.getPrimaryEmotion() : (d.getSelfEmotionTag() != null ? d.getSelfEmotionTag() : "UNCERTAIN"));
            List<String> kws = parseList(ana != null ? ana.getKeywords() : null);

            long daysDiff = Math.max(0, anchor.toEpochDay() - d.getEntryDate().toEpochDay());
            double recencyScore = Math.max(0.1, 1.0 - (daysDiff / (double) (clampedDays + 1)));
            double emotionScore = (currentEmotion != null && currentEmotion.equalsIgnoreCase(emo)) ? 0.6 : 0.0;
            long kwOverlap = kws.stream().filter(curKwSet::contains).count();
            double kwScore = Math.min(1.0, kwOverlap * 0.4);

            String rawContent = d.getContent() == null ? "" : d.getContent().replaceAll("\\s+", " ").trim();
            String snippet = rawContent.length() > 70 ? rawContent.substring(0, 70) + "..." : rawContent;

            scored.add(new MemoryItem(
                    d.getId(),
                    d.getEntryDate(),
                    d.getTitle(),
                    emo,
                    kws,
                    snippet,
                    recencyScore + emotionScore + kwScore
            ));
        }

        int maxItems = appProperties.getAgent().getMaxHistoryItems();
        int maxChars = appProperties.getAgent().getMaxHistoryChars();

        scored.sort((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        List<MemoryItem> selected = new ArrayList<>();
        int totalChars = 0;
        for (MemoryItem item : scored) {
            if (selected.size() >= maxItems) break;
            int itemChars = (item.getTitle() == null ? 0 : item.getTitle().length()) + item.getSnippet().length() + 30;
            if (totalChars + itemChars > maxChars && !selected.isEmpty()) break;
            selected.add(item);
            totalChars += itemChars;
        }
        return selected;
    }

    public String formatMemorySummary(List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return "无近期历史记录。";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryItem item : items) {
            sb.append(String.format("- [日记#%d | %s | 情绪:%s | 关键词:%s] 标题：%s；摘要：%s\n",
                    item.getDiaryId(),
                    item.getEntryDate(),
                    item.getEmotion(),
                    String.join("、", item.getKeywords()),
                    item.getTitle(),
                    item.getSnippet()));
        }
        return sb.toString();
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
