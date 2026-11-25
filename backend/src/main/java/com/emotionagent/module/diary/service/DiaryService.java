package com.emotionagent.module.diary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.emotionagent.common.BusinessException;
import com.emotionagent.common.MaskUtils;
import com.emotionagent.module.agent.service.EmotionAgentOrchestrator;
import com.emotionagent.module.audit.service.AuditService;
import com.emotionagent.module.diary.dto.DiaryCorrectionRequest;
import com.emotionagent.module.diary.dto.DiaryCreateRequest;
import com.emotionagent.module.diary.dto.DiaryDetailDto;
import com.emotionagent.module.diary.dto.DiaryUpdateRequest;
import com.emotionagent.module.diary.entity.AiSuggestion;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.AiSuggestionMapper;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAnalysisMapper analysisMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final EmotionAgentOrchestrator agentOrchestrator;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * 核心提交流程：先独立持久化原文（保证 AI 失败不影响原文保存），再按授权执行 NLP 分析与智能体建议生成。
     */
    public DiaryDetailDto createDiary(Long userId, DiaryCreateRequest req, String ip) {
        EmotionDiary diary = persistNewDiary(userId, req, ip);

        EmotionAnalysis analysis = null;
        AiSuggestion suggestion = null;
        if (!Boolean.FALSE.equals(diary.getAiConsent())) {
            try {
                analysis = agentOrchestrator.analyzeDiary(diary, req.getAnalyzerMethod());
                suggestion = agentOrchestrator.generateSuggestion(
                        diary, analysis, req.getRequestId(), req.getUseHistoryOverride());
            } catch (BusinessException bx) {
                // 如触发限流，保留原文与基础分析，不阻断日记创建
                log.warn("AI suggestion skipped/limited for diaryId={}: {}", diary.getId(), bx.getMessage());
            } catch (Exception ex) {
                log.error("AI processing failed for diaryId={}, diary already saved safely: {}", diary.getId(), ex.getMessage());
            }
        }
        return toDetailDto(diary, analysis, suggestion);
    }

    @Transactional
    protected EmotionDiary persistNewDiary(Long userId, DiaryCreateRequest req, String ip) {
        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setTitle(req.getTitle().trim());
        diary.setContent(req.getContent().trim());
        diary.setEntryDate(req.getEntryDate());
        diary.setSelfEmotionTag(normalizeTag(req.getSelfEmotionTag()));
        diary.setContentVersion(1);
        diary.setAiConsent(req.getAiConsent() == null || req.getAiConsent());
        diary.setMemoryIncluded(req.getMemoryIncluded() == null || req.getMemoryIncluded());
        diary.setCreatedAt(LocalDateTime.now());
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.insert(diary);

        auditService.record(userId, "USER", "CREATE_DIARY", "DIARY", diary.getId(),
                MaskUtils.safeTextMeta("diary", diary.getContent()) + ", date=" + diary.getEntryDate(), ip);
        return diary;
    }

    public Map<String, Object> listDiaries(Long userId, int page, int size,
                                           LocalDate startDate, LocalDate endDate,
                                           String emotion, String keyword) {
        Page<EmotionDiary> mpPage = new Page<>(Math.max(1, page), Math.min(50, Math.max(1, size)));
        LambdaQueryWrapper<EmotionDiary> wrapper = new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, userId)
                .orderByDesc(EmotionDiary::getEntryDate, EmotionDiary::getId);

        if (startDate != null) wrapper.ge(EmotionDiary::getEntryDate, startDate);
        if (endDate != null) wrapper.le(EmotionDiary::getEntryDate, endDate);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(EmotionDiary::getTitle, kw).or().like(EmotionDiary::getContent, kw));
        }

        Page<EmotionDiary> result = diaryMapper.selectPage(mpPage, wrapper);
        List<EmotionDiary> diaries = result.getRecords();

        Map<Long, EmotionAnalysis> analysisMap = loadLatestAnalyses(diaries);
        Map<Long, AiSuggestion> suggestionMap = loadLatestSuggestions(diaries);

        List<DiaryDetailDto> items = diaries.stream()
                .map(d -> toDetailDto(d, analysisMap.get(d.getId()), suggestionMap.get(d.getId())))
                .filter(dto -> emotion == null || emotion.isBlank()
                        || emotion.equalsIgnoreCase(dto.getEffectiveEmotion()))
                .toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("records", items);
        resp.put("total", emotion != null && !emotion.isBlank() ? items.size() : result.getTotal());
        resp.put("page", result.getCurrent());
        resp.put("size", result.getSize());
        return resp;
    }

    public DiaryDetailDto getDiary(Long userId, Long diaryId) {
        EmotionDiary diary = requireOwnedDiary(userId, diaryId);
        EmotionAnalysis ana = getLatestAnalysis(diaryId);
        AiSuggestion sug = getLatestSuggestion(diaryId);
        return toDetailDto(diary, ana, sug);
    }

    public DiaryDetailDto updateDiary(Long userId, Long diaryId, DiaryUpdateRequest req, String ip) {
        EmotionDiary diary = requireOwnedDiary(userId, diaryId);
        boolean textChanged = !Objects.equals(diary.getContent(), req.getContent().trim())
                || !Objects.equals(diary.getTitle(), req.getTitle().trim());

        diary.setTitle(req.getTitle().trim());
        diary.setContent(req.getContent().trim());
        diary.setEntryDate(req.getEntryDate());
        if (req.getSelfEmotionTag() != null) {
            diary.setSelfEmotionTag(normalizeTag(req.getSelfEmotionTag()));
        }
        if (req.getAiConsent() != null) diary.setAiConsent(req.getAiConsent());
        if (req.getMemoryIncluded() != null) diary.setMemoryIncluded(req.getMemoryIncluded());
        if (textChanged) {
            diary.setContentVersion((diary.getContentVersion() == null ? 1 : diary.getContentVersion()) + 1);
            // 正文变化后旧分析自动标记失效，等待重算
            analysisMapper.update(null, new LambdaUpdateWrapper<EmotionAnalysis>()
                    .eq(EmotionAnalysis::getDiaryId, diary.getId())
                    .set(EmotionAnalysis::getIsLatest, false));
        }
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.updateById(diary);

        auditService.record(userId, "USER", "UPDATE_DIARY", "DIARY", diary.getId(),
                "version=" + diary.getContentVersion() + ", textChanged=" + textChanged, ip);

        EmotionAnalysis ana = getLatestAnalysis(diaryId);
        AiSuggestion sug = getLatestSuggestion(diaryId);

        if (textChanged && !Boolean.FALSE.equals(req.getReanalyzeNow()) && !Boolean.FALSE.equals(diary.getAiConsent())) {
            ana = agentOrchestrator.analyzeDiary(diary, null);
            if (Boolean.TRUE.equals(req.getRegenerateSuggestion())) {
                sug = agentOrchestrator.generateSuggestion(diary, ana, UUID.randomUUID().toString(), null);
            }
        }
        return toDetailDto(diary, ana, sug);
    }

    /**
     * 用户手动纠正机器分析标签，保留“自评 vs 机器分析 vs 用户纠偏”三者边界。
     */
    @Transactional
    public DiaryDetailDto correctEmotion(Long userId, Long diaryId, DiaryCorrectionRequest req, String ip) {
        EmotionDiary diary = requireOwnedDiary(userId, diaryId);
        diary.setCorrectedEmotionTag(normalizeTag(req.getCorrectedEmotionTag()));
        diary.setCorrectedPolarity(normalizeTag(req.getCorrectedPolarity()));
        diary.setUpdatedAt(LocalDateTime.now());
        diaryMapper.updateById(diary);

        auditService.record(userId, "USER", "CORRECT_EMOTION", "DIARY", diaryId,
                "correctedEmotion=" + diary.getCorrectedEmotionTag() + ", polarity=" + diary.getCorrectedPolarity(), ip);

        return toDetailDto(diary, getLatestAnalysis(diaryId), getLatestSuggestion(diaryId));
    }

    public DiaryDetailDto reanalyze(Long userId, Long diaryId, String method, String ip) {
        EmotionDiary diary = requireOwnedDiary(userId, diaryId);
        EmotionAnalysis ana = agentOrchestrator.analyzeDiary(diary, method);
        auditService.record(userId, "USER", "REANALYZE_DIARY", "DIARY", diaryId,
                "method=" + ana.getAnalyzerMethod() + ", emotion=" + ana.getPrimaryEmotion(), ip);
        return toDetailDto(diary, ana, getLatestSuggestion(diaryId));
    }

    public DiaryDetailDto regenerateSuggestion(Long userId, Long diaryId, String requestId,
                                               Boolean useHistoryOverride, String ip) {
        EmotionDiary diary = requireOwnedDiary(userId, diaryId);
        EmotionAnalysis ana = getLatestAnalysis(diaryId);
        if (ana == null || !Objects.equals(ana.getContentVersion(), diary.getContentVersion())) {
            ana = agentOrchestrator.analyzeDiary(diary, null);
        }
        AiSuggestion sug = agentOrchestrator.generateSuggestion(diary, ana, requestId, useHistoryOverride);
        auditService.record(userId, "USER", "REGENERATE_SUGGESTION", "DIARY", diaryId,
                "status=" + sug.getStatus() + ", usedHistory=" + sug.getUsedHistory(), ip);
        return toDetailDto(diary, ana, sug);
    }

    /**
     * 删除日记并同步清理关联分析与建议（隐私彻底删除）。
     */
    @Transactional
    public void deleteDiary(Long userId, Long diaryId, String ip) {
        requireOwnedDiary(userId, diaryId);
        suggestionMapper.delete(new LambdaQueryWrapper<AiSuggestion>().eq(AiSuggestion::getDiaryId, diaryId));
        analysisMapper.delete(new LambdaQueryWrapper<EmotionAnalysis>().eq(EmotionAnalysis::getDiaryId, diaryId));
        diaryMapper.deleteById(diaryId);
        auditService.record(userId, "USER", "DELETE_DIARY", "DIARY", diaryId,
                "Deleted diary and cascaded analyses/suggestions", ip);
    }

    public EmotionDiary requireOwnedDiary(Long userId, Long diaryId) {
        EmotionDiary diary = diaryMapper.selectById(diaryId);
        if (diary == null) {
            throw new BusinessException(404, "情绪日记不存在");
        }
        if (!Objects.equals(diary.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问或操作他人的情绪日记");
        }
        return diary;
    }

    private EmotionAnalysis getLatestAnalysis(Long diaryId) {
        return analysisMapper.selectOne(new LambdaQueryWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getDiaryId, diaryId)
                .eq(EmotionAnalysis::getIsLatest, true)
                .orderByDesc(EmotionAnalysis::getId)
                .last("LIMIT 1"));
    }

    private AiSuggestion getLatestSuggestion(Long diaryId) {
        return suggestionMapper.selectOne(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getDiaryId, diaryId)
                .eq(AiSuggestion::getIsLatest, true)
                .orderByDesc(AiSuggestion::getId)
                .last("LIMIT 1"));
    }

    private Map<Long, EmotionAnalysis> loadLatestAnalyses(List<EmotionDiary> diaries) {
        if (diaries.isEmpty()) return Map.of();
        List<Long> ids = diaries.stream().map(EmotionDiary::getId).toList();
        return analysisMapper.selectList(new LambdaQueryWrapper<EmotionAnalysis>()
                .in(EmotionAnalysis::getDiaryId, ids)
                .eq(EmotionAnalysis::getIsLatest, true))
                .stream().collect(Collectors.toMap(EmotionAnalysis::getDiaryId, a -> a, (a, b) -> b));
    }

    private Map<Long, AiSuggestion> loadLatestSuggestions(List<EmotionDiary> diaries) {
        if (diaries.isEmpty()) return Map.of();
        List<Long> ids = diaries.stream().map(EmotionDiary::getId).toList();
        return suggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                .in(AiSuggestion::getDiaryId, ids)
                .eq(AiSuggestion::getIsLatest, true))
                .stream().collect(Collectors.toMap(AiSuggestion::getDiaryId, s -> s, (a, b) -> b));
    }

    private DiaryDetailDto toDetailDto(EmotionDiary d, EmotionAnalysis a, AiSuggestion s) {
        String effective = d.getCorrectedEmotionTag() != null && !d.getCorrectedEmotionTag().isBlank()
                ? d.getCorrectedEmotionTag()
                : (a != null ? a.getPrimaryEmotion() : (d.getSelfEmotionTag() != null ? d.getSelfEmotionTag() : "UNCERTAIN"));

        boolean stale = a == null || !Objects.equals(a.getContentVersion(), d.getContentVersion());

        DiaryDetailDto.AnalysisView anaView = null;
        if (a != null) {
            anaView = DiaryDetailDto.AnalysisView.builder()
                    .id(a.getId())
                    .contentVersion(a.getContentVersion())
                    .analyzerMethod(a.getAnalyzerMethod())
                    .polarity(a.getPolarity())
                    .primaryEmotion(a.getPrimaryEmotion())
                    .secondaryEmotions(parseJson(a.getSecondaryEmotions(), new TypeReference<>() {}, List.of()))
                    .emotionScores(parseJson(a.getEmotionScores(), new TypeReference<>() {}, Map.of()))
                    .intensityScore(a.getIntensityScore())
                    .keywords(parseJson(a.getKeywords(), new TypeReference<>() {}, List.of()))
                    .riskLevel(a.getRiskLevel())
                    .riskMatchedRules(parseJson(a.getRiskMatchedRules(), new TypeReference<>() {}, List.of()))
                    .explanation(a.getExplanation())
                    .analyzedAt(a.getAnalyzedAt())
                    .build();
        }

        DiaryDetailDto.SuggestionView sugView = null;
        if (s != null) {
            List<Long> refIds = parseJson(s.getReferencedDiaryIds(), new TypeReference<>() {}, List.of());
            List<DiaryDetailDto.ReferencedDiaryBrief> refBriefs = List.of();
            if (!refIds.isEmpty()) {
                List<EmotionDiary> refDiaries = diaryMapper.selectList(new LambdaQueryWrapper<EmotionDiary>()
                        .eq(EmotionDiary::getUserId, d.getUserId())
                        .in(EmotionDiary::getId, refIds));
                refBriefs = refDiaries.stream()
                        .map(rd -> DiaryDetailDto.ReferencedDiaryBrief.builder()
                                .id(rd.getId())
                                .title(rd.getTitle())
                                .entryDate(rd.getEntryDate())
                                .build())
                        .toList();
            }
            sugView = DiaryDetailDto.SuggestionView.builder()
                    .id(s.getId())
                    .contentVersion(s.getContentVersion())
                    .requestId(s.getRequestId())
                    .usedHistory(s.getUsedHistory())
                    .referencedDiaryIds(refIds)
                    .referencedDiaries(refBriefs)
                    .modelName(s.getModelName())
                    .promptVersion(s.getPromptVersion())
                    .status(s.getStatus())
                    .empathyText(s.getEmpathyText())
                    .actionItems(parseJson(s.getActionItems(), new TypeReference<>() {}, List.of()))
                    .disclaimer(s.getDisclaimer())
                    .validationPassed(s.getValidationPassed())
                    .validationNotes(s.getValidationNotes())
                    .agentTrace(parseJson(s.getAgentTrace(), new TypeReference<>() {}, List.of()))
                    .latencyMs(s.getLatencyMs())
                    .createdAt(s.getCreatedAt())
                    .build();
        }

        return DiaryDetailDto.builder()
                .id(d.getId())
                .title(d.getTitle())
                .content(d.getContent())
                .entryDate(d.getEntryDate())
                .selfEmotionTag(d.getSelfEmotionTag())
                .correctedEmotionTag(d.getCorrectedEmotionTag())
                .correctedPolarity(d.getCorrectedPolarity())
                .effectiveEmotion(effective)
                .contentVersion(d.getContentVersion())
                .aiConsent(d.getAiConsent())
                .memoryIncluded(d.getMemoryIncluded())
                .analysisStale(stale)
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .analysis(anaView)
                .suggestion(sugView)
                .build();
    }

    private String normalizeTag(String tag) {
        if (tag == null || tag.isBlank()) return null;
        return tag.trim().toUpperCase();
    }

    private <T> T parseJson(String raw, TypeReference<T> typeRef, T defaultVal) {
        if (raw == null || raw.isBlank()) return defaultVal;
        try {
            return objectMapper.readValue(raw, typeRef);
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
