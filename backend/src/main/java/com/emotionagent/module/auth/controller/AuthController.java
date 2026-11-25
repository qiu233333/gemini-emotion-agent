package com.emotionagent.module.auth.controller;

import com.emotionagent.common.ApiResponse;
import com.emotionagent.config.AppProperties;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.auth.dto.LoginRequest;
import com.emotionagent.module.auth.dto.RegisterRequest;
import com.emotionagent.module.auth.dto.UpdateProfileRequest;
import com.emotionagent.module.auth.dto.UserProfileDto;
import com.emotionagent.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppProperties appProperties;

    @GetMapping("/api/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean llmConfigured = appProperties.getLlm().getApiKey() != null
                && !appProperties.getLlm().getApiKey().isBlank();
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "llmProvider", appProperties.getLlm().getProvider(),
                "llmModel", appProperties.getLlm().getModel(),
                "llmLiveEnabled", llmConfigured,
                "nlpDefaultMethod", appProperties.getNlp().getDefaultMethod()
        ));
    }

    @GetMapping("/api/system/info")
    public ApiResponse<Map<String, Object>> systemInfo() {
        return ApiResponse.ok(Map.of(
                "name", "心迹 · 基于大语言模型的情绪记录与管理智能体系统",
                "disclaimer", "本系统定位为日常情绪记录、自我回顾与非医疗性质的辅助工具，不提供心理疾病诊断、医学量表评估或临床治疗服务。如遇紧急情绪危机，请立即联系可信任的亲友、学校心理健康中心或拨打 12356 / 120 / 110。",
                "llmProvider", appProperties.getLlm().getProvider(),
                "llmModel", appProperties.getLlm().getModel(),
                "llmLiveEnabled", appProperties.getLlm().getApiKey() != null && !appProperties.getLlm().getApiKey().isBlank()
        ));
    }

    @PostMapping("/api/auth/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req,
                                                     HttpServletRequest request) {
        return ApiResponse.ok("注册成功", authService.register(req, request.getRemoteAddr()));
    }

    @PostMapping("/api/auth/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                                  HttpServletRequest request) {
        return ApiResponse.ok("登录成功", authService.login(req, request.getRemoteAddr()));
    }

    @GetMapping("/api/user/profile")
    public ApiResponse<UserProfileDto> profile() {
        return ApiResponse.ok(authService.getProfile(SecurityUtils.currentUserId()));
    }

    @PutMapping("/api/user/profile")
    public ApiResponse<UserProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest req,
                                                     HttpServletRequest request) {
        return ApiResponse.ok("资料已更新", authService.updateProfile(
                SecurityUtils.currentUserId(), req, request.getRemoteAddr()));
    }

    @PostMapping("/api/user/memory/clear")
    public ApiResponse<Map<String, Object>> clearMemory(HttpServletRequest request) {
        int affected = authService.clearHistoryMemory(SecurityUtils.currentUserId(), request.getRemoteAddr());
        return ApiResponse.ok("已清除历史记忆关联", Map.of("clearedDiaryCount", affected));
    }
}
