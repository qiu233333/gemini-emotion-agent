package com.emotionagent.module.stats.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 情绪统计与可视化服务：
 * - 严格遵守开题报告口径：缺失日期按无记录 (null) 处理，不填充为中性情绪；
 * - 同日多条记录采用一致均值聚合，并明确输出当日记录数，避免把频率误解为程度；
 * - 所有统计接口均返回统计起止区间与有效记录条数。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAnalysisMapper analysisMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getDashboardStats(Long userId, int days) {
        int window = Math.max(7, Math.min(90, days));
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(window - 1L);

        List<EmotionDiary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .ge(EmotionDiary::getEntryDate, start)
                .le(EmotionDiary::getEntryDate, end)
                .orderByAsc(EmotionDiary::getEntryDate, EmotionDiary::getId));

        Map<Long, EmotionAnalysis> anaMap = loadAnalysisMap(diaries);

        // 1. 按天聚合趋势（缺失日期严格留 null，绝不补 0 或中性分）
        Map<LocalDate, List<EmotionDiary>> byDate = diaries.stream()
                .collect(Collectors.groupingBy(EmotionDiary::getEntryDate));

        List<String> dates = new ArrayList<>();
        List<Double> avgIntensities = new ArrayList<>();
        List<Double> moodValenceScores = new ArrayList<>(); // -100 ~ +100 情绪倾向可视化辅助值
        List<Integer> dailyCounts = new ArrayList<>();
        List<String> dailyPrimaryEmotions = new ArrayList<>();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dates.add(d.toString());
            List<EmotionDiary> dayList = byDate.get(d);
            if (dayList == null || dayList.isEmpty()) {
                avgIntensities.add(null);
                moodValenceScores.add(null);
                dailyCounts.add(0);
                dailyPrimaryEmotions.add(null);
            } else {
                dailyCounts.add(dayList.size());
                double sumInt = 0;
                double sumVal = 0;
                int intCount = 0;
                Map<String, Integer> emoFreq = new HashMap<>();
                for (EmotionDiary diary : dayList) {
                    EmotionAnalysis a = anaMap.get(diary.getId());
                    String emo = effectiveEmotion(diary, a);
                    emoFreq.merge(emo, 1, Integer::sum);
                    if (a != null && a.getIntensityScore() != null) {
                        sumInt += a.getIntensityScore();
                        intCount++;
                    }
                    sumVal += emotionValence(emo, a != null ? a.getIntensityScore() : 50);
                }
                avgIntensities.add(intCount > 0 ? Math.round((sumInt / intCount) * 10.0) / 10.0 : null);
                moodValenceScores.add(Math.round((sumVal / dayList.size()) * 10.0) / 10.0);
                String dominant = emoFreq.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse("UNCERTAIN");
                dailyPrimaryEmotions.add(dominant);
            }
        }

        // 2. 情绪类型分布 & 极性分布
        Map<String, Integer> emotionDist = new LinkedHashMap<>();
        Map<String, Integer> polarityDist = new LinkedHashMap<>();
        Map<String, Integer> keywordFreq = new HashMap<>();

        for (EmotionDiary diary : diaries) {
            EmotionAnalysis a = anaMap.get(diary.getId());
            String emo = effectiveEmotion(diary, a);
            emotionDist.merge(emo, 1, Integer::sum);

            String pol = diary.getCorrectedPolarity() != null && !diary.getCorrectedPolarity().isBlank()
                    ? diary.getCorrectedPolarity()
                    : (a != null ? a.getPolarity() : "UNCERTAIN");
            polarityDist.merge(pol, 1, Integer::sum);

            if (a != null && a.getKeywords() != null) {
                for (String kw : parseList(a.getKeywords())) {
                    keywordFreq.merge(kw, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> topKeywords = keywordFreq.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(20)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "value", e.getValue()))
                .toList();

        Long totalAllTime = diaryMapper.selectCount(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("startDate", start.toString());
        res.put("endDate", end.toString());
        res.put("windowDays", window);
        res.put("recordCount", diaries.size());
        res.put("recordedDays", byDate.size());
        res.put("totalAllTime", totalAllTime);
        res.put("dates", dates);
        res.put("avgIntensities", avgIntensities);
        res.put("moodValenceScores", moodValenceScores);
        res.put("dailyCounts", dailyCounts);
        res.put("dailyPrimaryEmotions", dailyPrimaryEmotions);
        res.put("emotionDistribution", emotionDist);
        res.put("polarityDistribution", polarityDist);
        res.put("topKeywords", topKeywords);
        return res;
    }

    /**
     * 情绪日历（进阶功能）：按月返回每日主导情绪、条数与日记简要列表。
     */
    public Map<String, Object> getEmotionCalendar(Long userId, String monthStr) {
        YearMonth ym = (monthStr == null || monthStr.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<EmotionDiary> diaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .ge(EmotionDiary::getEntryDate, start)
                .le(EmotionDiary::getEntryDate, end)
                .orderByAsc(EmotionDiary::getEntryDate, EmotionDiary::getId));
        Map<Long, EmotionAnalysis> anaMap = loadAnalysisMap(diaries);

        Map<String, List<Map<String, Object>>> dayItems = new LinkedHashMap<>();
        for (EmotionDiary d : diaries) {
            EmotionAnalysis a = anaMap.get(d.getId());
            String dateKey = d.getEntryDate().toString();
            dayItems.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(Map.of(
                    "id", d.getId(),
                    "title", d.getTitle(),
                    "emotion", effectiveEmotion(d, a),
                    "intensity", a != null && a.getIntensityScore() != null ? a.getIntensityScore() : 0
            ));
        }

        Map<String, Object> calendar = new LinkedHashMap<>();
        calendar.put("month", ym.toString());
        calendar.put("startDate", start.toString());
        calendar.put("endDate", end.toString());
        calendar.put("recordCount", diaries.size());
        calendar.put("days", dayItems);
        return calendar;
    }

    private Map<Long, EmotionAnalysis> loadAnalysisMap(List<EmotionDiary> diaries) {
        if (diaries.isEmpty()) return Map.of();
        List<Long> ids = diaries.stream().map(EmotionDiary::getId).toList();
        return analysisMapper.selectList(new LambdaQueryWrapper<EmotionAnalysis>()
                .in(EmotionAnalysis::getDiaryId, ids)
                .eq(EmotionAnalysis::getIsLatest, true))
                .stream().collect(Collectors.toMap(EmotionAnalysis::getDiaryId, a -> a, (a, b) -> b));
    }

    private String effectiveEmotion(EmotionDiary d, EmotionAnalysis a) {
        if (d.getCorrectedEmotionTag() != null && !d.getCorrectedEmotionTag().isBlank()) {
            return d.getCorrectedEmotionTag();
        }
        if (a != null && a.getPrimaryEmotion() != null) {
            return a.getPrimaryEmotion();
        }
        return d.getSelfEmotionTag() != null ? d.getSelfEmotionTag() : "UNCERTAIN";
    }

    private double emotionValence(String emotion, Integer intensity) {
        double mag = intensity == null ? 50.0 : intensity;
        return switch (emotion == null ? "" : emotion.toUpperCase()) {
            case "JOY" -> mag;
            case "CALM" -> mag * 0.45;
            case "ANXIETY" -> -mag * 0.75;
            case "SADNESS" -> -mag * 0.85;
            case "ANGER" -> -mag * 0.8;
            case "MIXED" -> 0.0;
            default -> 0.0;
        };
    }

    private List<String> parseList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
