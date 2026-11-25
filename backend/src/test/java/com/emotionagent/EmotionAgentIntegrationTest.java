package com.emotionagent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import com.emotionagent.module.diary.entity.AiSuggestion;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.mapper.AiSuggestionMapper;
import com.emotionagent.module.diary.mapper.EmotionAnalysisMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmotionAgentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmotionAnalysisMapper analysisMapper;
    @Autowired private AiSuggestionMapper suggestionMapper;

    private String tokenA;
    private String tokenB;
    private String adminToken;

    @BeforeEach
    void setupUsers() throws Exception {
        ensureUser("alice_test", "Pass@123", "Alice", "USER");
        ensureUser("bob_test", "Pass@123", "Bob", "USER");
        ensureUser("admin_test", "Admin@123", "Admin", "ADMIN");

        tokenA = login("alice_test", "Pass@123");
        tokenB = login("bob_test", "Pass@123");
        adminToken = login("admin_test", "Admin@123");
    }

    private void ensureUser(String username, String pwd, String nickname, String role) {
        if (userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) != null) return;
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(pwd));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus("ACTIVE");
        u.setAiMemoryEnabled(true);
        u.setMemoryWindowDays(30);
        u.setDailyAiLimit(50);
        userMapper.insert(u);
    }

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        String resp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("token").asText();
    }

    @Test
    void testFullLifecycleIdorSafetyAndCascadeDelete() throws Exception {
        // 1. Alice 创建第一篇较早的日记（作为历史记忆）
        String d1Req = objectMapper.writeValueAsString(Map.of(
                "title", "准备课程大作业",
                "content", "这周ddl很紧，有点焦虑睡不着。",
                "entryDate", LocalDate.now().minusDays(2).toString(),
                "selfEmotionTag", "ANXIETY",
                "aiConsent", true
        ));
        String d1Resp = mockMvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(d1Req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.primaryEmotion").value("ANXIETY"))
                .andReturn().getResponse().getContentAsString();
        Long d1Id = objectMapper.readTree(d1Resp).path("data").path("id").asLong();

        // 2. Alice 创建今日日记，验证智能体检索到了历史记忆 d1Id 且可追溯
        String d2Req = objectMapper.writeValueAsString(Map.of(
                "title", "继续赶ddl",
                "content", "今天依然感觉压力很大，有点焦虑但已经完成了一半。",
                "entryDate", LocalDate.now().toString(),
                "selfEmotionTag", "ANXIETY",
                "aiConsent", true
        ));
        String d2Resp = mockMvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(d2Req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suggestion.usedHistory").value(true))
                .andReturn().getResponse().getContentAsString();
        Long d2Id = objectMapper.readTree(d2Resp).path("data").path("id").asLong();

        // 3. 越权测试 (IDOR)：Bob 访问/修改/删除 Alice 的日记必须被 403 拒绝
        mockMvc.perform(get("/api/diaries/" + d2Id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/diaries/" + d2Id).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 4. 高风险输入短路测试：必须立即返回 HIGH_RISK_SAFE 与援助热线提示
        String crisisReq = objectMapper.writeValueAsString(Map.of(
                "title", "很痛苦",
                "content", "感觉活不下去了，甚至有轻生的念头。",
                "entryDate", LocalDate.now().toString(),
                "aiConsent", true
        ));
        mockMvc.perform(post("/api/diaries")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crisisReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysis.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.suggestion.status").value("HIGH_RISK_SAFE"));

        // 5. 统计缺失日期留 null 测试（昨天无记录，avgIntensities 中应包含 null）
        String statsResp = mockMvc.perform(get("/api/stats/dashboard?days=7")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode intensities = objectMapper.readTree(statsResp).path("data").path("avgIntensities");
        boolean hasNullGap = false;
        for (JsonNode n : intensities) {
            if (n.isNull()) hasNullGap = true;
        }
        assertTrue(hasNullGap, "缺失日期必须保留 null，不得填充为中性情绪");

        // 6. 管理后台权限与隐私边界测试
        mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
        String adminOverview = mockMvc.perform(get("/api/admin/overview").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(adminOverview.contains("轻生的念头"), "管理后台绝不能泄露私人日记正文");

        // 7. 删除联动清理测试：删除日记后分析与建议记录应同步清空
        mockMvc.perform(delete("/api/diaries/" + d1Id).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
        assertEquals(0, analysisMapper.selectCount(new LambdaQueryWrapper<EmotionAnalysis>().eq(EmotionAnalysis::getDiaryId, d1Id)));
        assertEquals(0, suggestionMapper.selectCount(new LambdaQueryWrapper<AiSuggestion>().eq(AiSuggestion::getDiaryId, d1Id)));
    }
}
