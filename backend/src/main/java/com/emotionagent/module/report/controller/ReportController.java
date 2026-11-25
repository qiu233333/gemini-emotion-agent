package com.emotionagent.module.report.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.report.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(reportService.listReports(SecurityUtils.currentUserId()));
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@RequestParam(defaultValue = "WEEKLY") String type,
                                                     HttpServletRequest request) {
        return ApiResponse.ok("周期报告已生成", reportService.generateReport(
                SecurityUtils.currentUserId(), type, request.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reportService.deleteReport(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok();
    }
}
