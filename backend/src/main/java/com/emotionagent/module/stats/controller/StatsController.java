package com.emotionagent.module.stats.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(statsService.getDashboardStats(SecurityUtils.currentUserId(), days));
    }

    @GetMapping("/calendar")
    public ApiResponse<Map<String, Object>> calendar(@RequestParam(required = false) String month) {
        return ApiResponse.ok(statsService.getEmotionCalendar(SecurityUtils.currentUserId(), month));
    }
}
