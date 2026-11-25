package com.emotionagent.module.diary.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.diary.dto.DiaryCorrectionRequest;
import com.emotionagent.module.diary.dto.DiaryCreateRequest;
import com.emotionagent.module.diary.dto.DiaryDetailDto;
import com.emotionagent.module.diary.dto.DiaryUpdateRequest;
import com.emotionagent.module.diary.service.DiaryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @PostMapping
    public ApiResponse<DiaryDetailDto> create(@Valid @RequestBody DiaryCreateRequest req,
                                              HttpServletRequest request) {
        return ApiResponse.ok("日记已保存", diaryService.createDiary(
                SecurityUtils.currentUserId(), req, request.getRemoteAddr()));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String emotion,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(diaryService.listDiaries(
                SecurityUtils.currentUserId(), page, size, startDate, endDate, emotion, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiaryDetailDto> get(@PathVariable Long id) {
        return ApiResponse.ok(diaryService.getDiary(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiaryDetailDto> update(@PathVariable Long id,
                                              @Valid @RequestBody DiaryUpdateRequest req,
                                              HttpServletRequest request) {
        return ApiResponse.ok("日记已更新", diaryService.updateDiary(
                SecurityUtils.currentUserId(), id, req, request.getRemoteAddr()));
    }

    @PatchMapping("/{id}/correct")
    public ApiResponse<DiaryDetailDto> correct(@PathVariable Long id,
                                               @RequestBody DiaryCorrectionRequest req,
                                               HttpServletRequest request) {
        return ApiResponse.ok("已保存你的情绪纠偏", diaryService.correctEmotion(
                SecurityUtils.currentUserId(), id, req, request.getRemoteAddr()));
    }

    @PostMapping("/{id}/reanalyze")
    public ApiResponse<DiaryDetailDto> reanalyze(@PathVariable Long id,
                                                 @RequestParam(required = false) String method,
                                                 HttpServletRequest request) {
        return ApiResponse.ok("已重新分析情绪", diaryService.reanalyze(
                SecurityUtils.currentUserId(), id, method, request.getRemoteAddr()));
    }

    @PostMapping("/{id}/regenerate-suggestion")
    public ApiResponse<DiaryDetailDto> regenerateSuggestion(
            @PathVariable Long id,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) Boolean useHistoryOverride,
            HttpServletRequest request) {
        return ApiResponse.ok("已生成新建议", diaryService.regenerateSuggestion(
                SecurityUtils.currentUserId(), id, requestId, useHistoryOverride, request.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        diaryService.deleteDiary(SecurityUtils.currentUserId(), id, request.getRemoteAddr());
        return ApiResponse.ok();
    }
}
