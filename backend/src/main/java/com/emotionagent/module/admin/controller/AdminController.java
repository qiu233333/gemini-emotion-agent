package com.emotionagent.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.common.ApiResponse;
import com.emotionagent.common.BusinessException;
import com.emotionagent.common.MaskUtils;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.audit.entity.AuditLog;
import com.emotionagent.module.audit.mapper.AuditLogMapper;
import com.emotionagent.module.audit.service.AuditService;
import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import com.emotionagent.module.chat.mapper.ChatSessionMapper;
import com.emotionagent.module.diary.entity.AiSuggestion;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.mapper.AiSuggestionMapper;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.emotionagent.module.feedback.entity.UserFeedback;
import com.emotionagent.module.feedback.mapper.UserFeedbackMapper;
import com.emotionagent.module.report.mapper.PeriodicReportMapper;
import com.emotionagent.module.risk.entity.RiskRule;
import com.emotionagent.module.risk.mapper.RiskRuleMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 后台管理接口（仅 ADMIN 角色可访问）。
 * 隐私边界约束：所有接口仅提供脱敏统计、账户状态、风险规则与用户反馈管理，严禁返回用户私人日记正文。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SysUserMapper userMapper;
    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAnalysisMapper analysisMapper;
    private final AiSuggestionMapper suggestionMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final PeriodicReportMapper reportMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final UserFeedbackMapper feedbackMapper;
    private final AuditLogMapper auditLogMapper;
    private final AuditService auditService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        long totalUsers = userMapper.selectCount(null);
        long activeUsers = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, "ACTIVE"));
        long totalDiaries = diaryMapper.selectCount(null);
        long totalSuggestions = suggestionMapper.selectCount(null);
        long liveAiSuggestions = suggestionMapper.selectCount(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getStatus, "SUCCESS"));
        long highRiskAlerts = analysisMapper.selectCount(new LambdaQueryWrapper<EmotionAnalysis>()
                .eq(EmotionAnalysis::getRiskLevel, "HIGH").eq(EmotionAnalysis::getIsLatest, true));
        long totalChatSessions = chatSessionMapper.selectCount(null);
        long totalReports = reportMapper.selectCount(null);
        long pendingFeedbacks = feedbackMapper.selectCount(new LambdaQueryWrapper<UserFeedback>()
                .eq(UserFeedback::getStatus, "PENDING"));
        long enabledRules = riskRuleMapper.selectCount(new LambdaQueryWrapper<RiskRule>()
                .eq(RiskRule::getEnabled, true));

        // 脱敏聚合情绪分布（仅统计总体占比，不关联任何具体日记内容）
        List<EmotionAnalysis> latestList = analysisMapper.selectList(
                new LambdaQueryWrapper<EmotionAnalysis>().eq(EmotionAnalysis::getIsLatest, true));
        Map<String, Integer> globalEmotionDist = new LinkedHashMap<>();
        for (EmotionAnalysis a : latestList) {
            globalEmotionDist.merge(a.getPrimaryEmotion(), 1, Integer::sum);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("activeUsers", activeUsers);
        data.put("totalDiaries", totalDiaries);
        data.put("totalSuggestions", totalSuggestions);
        data.put("liveAiSuggestions", liveAiSuggestions);
        data.put("highRiskAlerts", highRiskAlerts);
        data.put("totalChatSessions", totalChatSessions);
        data.put("totalReports", totalReports);
        data.put("pendingFeedbacks", pendingFeedbacks);
        data.put("enabledRules", enabledRules);
        data.put("globalEmotionDistribution", globalEmotionDist);
        data.put("privacyNotice", "管理员视图遵循最小权限原则，仅展示系统运行聚合统计，不展示任何用户私人日记正文或聊天原文。");
        return ApiResponse.ok(data);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> listUsers(@RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(w -> w.like(SysUser::getUsername, k).or().like(SysUser::getNickname, k));
        }
        List<SysUser> users = userMapper.selectList(qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Long diaryCnt = diaryMapper.selectCount(new LambdaQueryWrapper<com.emotionagent.module.diary.entity.EmotionDiary>()
                    .eq(com.emotionagent.module.diary.entity.EmotionDiary::getUserId, u.getId()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("nickname", u.getNickname());
            row.put("emailMasked", MaskUtils.maskEmail(u.getEmail()));
            row.put("role", u.getRole());
            row.put("status", u.getStatus());
            row.put("aiMemoryEnabled", u.getAiMemoryEnabled());
            row.put("dailyAiLimit", u.getDailyAiLimit());
            row.put("diaryCount", diaryCnt);
            row.put("createdAt", u.getCreatedAt());
            result.add(row);
        }
        return ApiResponse.ok(result);
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long id,
                                              @RequestBody Map<String, Object> body,
                                              HttpServletRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(404, "用户不存在");
        if (Objects.equals(id, SecurityUtils.currentUserId())) {
            throw new BusinessException("不能禁用当前正在使用的管理员账号");
        }
        if (body.get("status") != null) {
            String st = String.valueOf(body.get("status")).toUpperCase();
            if (!"ACTIVE".equals(st) && !"DISABLED".equals(st)) {
                throw new BusinessException("非法状态值");
            }
            user.setStatus(st);
        }
        if (body.get("dailyAiLimit") != null) {
            int limit = Integer.parseInt(String.valueOf(body.get("dailyAiLimit")));
            if (limit < 1 || limit > 500) throw new BusinessException("每日调用额度需在 1-500 之间");
            user.setDailyAiLimit(limit);
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        auditService.record(SecurityUtils.currentUserId(), "ADMIN", "UPDATE_USER_STATUS", "USER", id,
                "status=" + user.getStatus() + ", dailyAiLimit=" + user.getDailyAiLimit(), request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/risk-rules")
    public ApiResponse<List<RiskRule>> listRiskRules() {
        return ApiResponse.ok(riskRuleMapper.selectList(new LambdaQueryWrapper<RiskRule>().orderByDesc(RiskRule::getId)));
    }

    @PostMapping("/risk-rules")
    public ApiResponse<RiskRule> createRiskRule(@RequestBody RiskRule rule, HttpServletRequest request) {
        validateRiskRule(rule);
        rule.setId(null);
        rule.setUpdatedBy(SecurityUtils.currentUserId());
        rule.setEnabled(rule.getEnabled() == null || rule.getEnabled());
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        riskRuleMapper.insert(rule);
        auditService.record(SecurityUtils.currentUserId(), "ADMIN", "CREATE_RISK_RULE", "RISK_RULE", rule.getId(),
                "name=" + rule.getName() + ", severity=" + rule.getSeverity(), request.getRemoteAddr());
        return ApiResponse.ok("风险规则已创建", rule);
    }

    @PutMapping("/risk-rules/{id}")
    public ApiResponse<RiskRule> updateRiskRule(@PathVariable Long id,
                                                @RequestBody RiskRule req,
                                                HttpServletRequest request) {
        RiskRule existing = riskRuleMapper.selectById(id);
        if (existing == null) throw new BusinessException(404, "风险规则不存在");
        validateRiskRule(req);
        existing.setName(req.getName().trim());
        existing.setPattern(req.getPattern().trim());
        existing.setMatchType(req.getMatchType());
        existing.setSeverity(req.getSeverity());
        existing.setCategory(req.getCategory());
        existing.setSafeTemplate(req.getSafeTemplate());
        existing.setEnabled(req.getEnabled() == null || req.getEnabled());
        existing.setUpdatedBy(SecurityUtils.currentUserId());
        existing.setUpdatedAt(LocalDateTime.now());
        riskRuleMapper.updateById(existing);
        auditService.record(SecurityUtils.currentUserId(), "ADMIN", "UPDATE_RISK_RULE", "RISK_RULE", id,
                "name=" + existing.getName() + ", enabled=" + existing.getEnabled(), request.getRemoteAddr());
        return ApiResponse.ok("风险规则已更新", existing);
    }

    @DeleteMapping("/risk-rules/{id}")
    public ApiResponse<Void> deleteRiskRule(@PathVariable Long id, HttpServletRequest request) {
        riskRuleMapper.deleteById(id);
        auditService.record(SecurityUtils.currentUserId(), "ADMIN", "DELETE_RISK_RULE", "RISK_RULE", id,
                "Deleted risk rule", request.getRemoteAddr());
        return ApiResponse.ok();
    }

    @GetMapping("/feedback")
    public ApiResponse<List<Map<String, Object>>> listFeedback(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<UserFeedback> qw = new LambdaQueryWrapper<UserFeedback>()
                .orderByDesc(UserFeedback::getCreatedAt);
        if (status != null && !status.isBlank()) {
            qw.eq(UserFeedback::getStatus, status.toUpperCase());
        }
        List<UserFeedback> list = feedbackMapper.selectList(qw);
        Map<Long, String> userNames = new HashMap<>();
        userMapper.selectList(null).forEach(u -> userNames.put(u.getId(), u.getUsername()));

        List<Map<String, Object>> resp = new ArrayList<>();
        for (UserFeedback f : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getId());
            m.put("userId", f.getUserId());
            m.put("username", userNames.getOrDefault(f.getUserId(), "user#" + f.getUserId()));
            m.put("targetType", f.getTargetType());
            m.put("targetId", f.getTargetId());
            m.put("rating", f.getRating());
            m.put("comment", f.getComment());
            m.put("status", f.getStatus());
            m.put("adminReply", f.getAdminReply());
            m.put("createdAt", f.getCreatedAt());
            resp.add(m);
        }
        return ApiResponse.ok(resp);
    }

    @PatchMapping("/feedback/{id}/resolve")
    public ApiResponse<UserFeedback> resolveFeedback(@PathVariable Long id,
                                                     @RequestBody Map<String, String> body,
                                                     HttpServletRequest request) {
        UserFeedback fb = feedbackMapper.selectById(id);
        if (fb == null) throw new BusinessException(404, "反馈记录不存在");
        fb.setAdminReply(body != null ? body.getOrDefault("adminReply", "感谢反馈，已记录处理。") : "已处理");
        fb.setStatus("RESOLVED");
        fb.setHandledBy(SecurityUtils.currentUserId());
        fb.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.updateById(fb);
        auditService.record(SecurityUtils.currentUserId(), "ADMIN", "RESOLVE_FEEDBACK", "FEEDBACK", id,
                "Resolved user feedback", request.getRemoteAddr());
        return ApiResponse.ok("反馈已回复并标记处理完成", fb);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLog>> auditLogs(@RequestParam(defaultValue = "50") int limit) {
        int lim = Math.min(200, Math.max(10, limit));
        return ApiResponse.ok(auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId)
                .last("LIMIT " + lim)));
    }

    private void validateRiskRule(RiskRule r) {
        if (r.getName() == null || r.getName().isBlank()) throw new BusinessException("规则名称不能为空");
        if (r.getPattern() == null || r.getPattern().isBlank()) throw new BusinessException("匹配词或表达式不能为空");
        if (r.getSafeTemplate() == null || r.getSafeTemplate().isBlank()) throw new BusinessException("安全提示模板不能为空");
        if (r.getMatchType() == null || r.getMatchType().isBlank()) r.setMatchType("KEYWORD");
        if (r.getSeverity() == null || r.getSeverity().isBlank()) r.setSeverity("HIGH");
        if (r.getCategory() == null || r.getCategory().isBlank()) r.setCategory("通用风控");
    }
}
