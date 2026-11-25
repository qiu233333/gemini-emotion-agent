package com.emotionagent.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.module.agent.service.EmotionAgentOrchestrator;
import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import com.emotionagent.module.diary.entity.EmotionAnalysis;
import com.emotionagent.module.diary.entity.EmotionDiary;
import com.emotionagent.module.diary.mapper.EmotionDiaryMapper;
import com.emotionagent.module.feedback.entity.UserFeedback;
import com.emotionagent.module.feedback.mapper.UserFeedbackMapper;
import com.emotionagent.module.risk.entity.RiskRule;
import com.emotionagent.module.risk.mapper.RiskRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 首次启动幂等初始化默认账号、风险规则与贴合大学生场景的演示情绪日记。
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final RiskRuleMapper riskRuleMapper;
    private final EmotionDiaryMapper diaryMapper;
    private final EmotionAgentOrchestrator orchestrator;
    private final UserFeedbackMapper feedbackMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        try {
            SysUser admin = ensureUser("admin", "Admin@123", "系统管理员", "admin@campus.edu.cn", "ADMIN");
            SysUser student = ensureUser("student", "Student@123", "林清欢（演示同学）", "student@campus.edu.cn", "USER");
            ensureUser("student2", "Student@123", "陈知行（对比测试账号）", "student2@campus.edu.cn", "USER");

            seedRiskRulesIfEmpty(admin.getId());
            seedDemoDiariesIfEmpty(student);
            seedDemoFeedbackIfEmpty(student.getId());
            log.info("DataInitializer completed successfully.");
        } catch (Exception ex) {
            log.warn("DataInitializer skipped or partial: {}", ex.getMessage());
        }
    }

    private SysUser ensureUser(String username, String rawPwd, String nickname, String email, String role) {
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (existing != null) return existing;

        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(rawPwd));
        u.setNickname(nickname);
        u.setEmail(email);
        u.setBio("USER".equals(role) ? "计算机专业大四学生，正在认真推进毕业设计与求职。" : "负责系统安全规则与日常运维管理。");
        u.setRole(role);
        u.setStatus("ACTIVE");
        u.setAiMemoryEnabled(true);
        u.setMemoryWindowDays(30);
        u.setDailyAiLimit(30);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    private void seedRiskRulesIfEmpty(Long adminId) {
        Long cnt = riskRuleMapper.selectCount(null);
        if (cnt != null && cnt > 0) return;

        List<RiskRule> rules = List.of(
                buildRule("自伤倾向关键词", "自杀|轻生|不想活|结束生命|割腕|跳楼|自残|伤害自己|活不下去", "REGEX", "HIGH", "危机干预",
                        "我注意到你此刻正在承受非常沉重的痛苦。请先停下来照顾好自己——建议立即联系身边信任的家人、朋友或辅导员，也可以拨打全国心理援助热线 12356、联系学校心理健康中心，紧急情况请拨打 120 / 110。", adminId),
                buildRule("极端绝望表述", "没有任何希望了|世界没有我会更好|想彻底消失|活着没意思|不如死了", "REGEX", "HIGH", "危机干预",
                        "听到你这么难受我很担心你。请优先联系现实中可以陪伴你的人（室友、家人、老师或学校心理中心），也可以拨打全国心理援助热线 12356 倾诉。", adminId),
                buildRule("持续严重失眠与崩溃预警", "连续.*失眠|彻底崩溃|喘不过气|每天都在哭", "REGEX", "LOW", "持续压力提醒",
                        "你近期似乎承受了较大的持续压力。除了记录情绪外，也建议和信任的朋友聊聊，或预约学校心理咨询中心做一次轻松的沟通。", adminId)
        );
        rules.forEach(riskRuleMapper::insert);
    }

    private RiskRule buildRule(String name, String pattern, String matchType, String severity,
                               String category, String template, Long adminId) {
        RiskRule r = new RiskRule();
        r.setName(name);
        r.setPattern(pattern);
        r.setMatchType(matchType);
        r.setSeverity(severity);
        r.setCategory(category);
        r.setSafeTemplate(template);
        r.setEnabled(true);
        r.setUpdatedBy(adminId);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }

    private void seedDemoDiariesIfEmpty(SysUser student) {
        Long cnt = diaryMapper.selectCount(new LambdaQueryWrapper<EmotionDiary>()
                .eq(EmotionDiary::getUserId, student.getId()));
        if (cnt != null && cnt > 0) return;

        LocalDate today = LocalDate.now();
        record DemoEntry(int daysAgo, String title, String content, String selfTag) {}

        // 刻意安排包含缺失日期（检验折线图不填充中性）、同日双记录、否定表达与转折混合情绪
        List<DemoEntry> entries = List.of(
                new DemoEntry(13, "开题调研起步", "今天去图书馆整理了情感分析和大模型智能体的资料，虽然任务量不小，但慢慢理出了思路，整体比较平静踏实。", "CALM"),
                new DemoEntry(11, "秋招简历石沉大海", "投出去十几份秋招简历一直没有回复，晚上在宿舍有点焦虑和迷茫，躺在床上翻来覆去睡不着，担心自己能力不够。", "ANXIETY"),
                new DemoEntry(10, "小组作业沟通不畅", "今天课程汇报排练时组员临近截止还在甩锅，态度特别敷衍，气得我真服了，觉得非常不公平。", "ANGER"),
                new DemoEntry(8, "和好友操场散步", "晚上和好朋友去操场慢跑并聊了很久，把之前的烦恼都倾诉出来后松了一口气，心情缓和安稳了不少。", "CALM"),
                new DemoEntry(6, "模拟面试没发挥好", "下午参加了一场技术模拟面试，算法题卡壳了，回来之后并不开心，觉得有点遗憾和失落，提不起劲看书。", "SADNESS"),
                new DemoEntry(5, "调试了一整天的 Bug", "后端权限校验报了一下午错，压力很大又心累，赶着课程 ddl 真的有点卷不动，头晕脑胀的。", "ANXIETY"),
                new DemoEntry(3, "核心接口终于跑通", "把情绪分析模块和数据库联调成功了！导师看后给了肯定和表扬，特别开心也很有成就感，晚上吃了一顿好吃的庆祝。", "JOY"),
                new DemoEntry(2, "准备中期答辩 PPT", "上午改完论文提纲有点担心答辩被问住，但是看到系统已经能流畅演示仍然期待展示自己的成果。", "MIXED"),
                new DemoEntry(2, "傍晚晚霞很治愈", "傍晚从实验楼出来看到操场上空的晚霞，听着歌散步回宿舍，整个人放松又舒服，感觉充满干劲。", "JOY"),
                new DemoEntry(0, "记录今天的学习节奏", "今天早起在自习室高效完成了接口测试和图表优化，虽然还有几处细节待打磨，但心里很踏实，对接下来很有信心。", "JOY")
        );

        for (DemoEntry e : entries) {
            EmotionDiary d = new EmotionDiary();
            d.setUserId(student.getId());
            d.setTitle(e.title());
            d.setContent(e.content());
            d.setEntryDate(today.minusDays(e.daysAgo()));
            d.setSelfEmotionTag(e.selfTag());
            d.setContentVersion(1);
            d.setAiConsent(true);
            d.setMemoryIncluded(true);
            d.setCreatedAt(LocalDateTime.now().minusDays(e.daysAgo()));
            d.setUpdatedAt(LocalDateTime.now().minusDays(e.daysAgo()));
            diaryMapper.insert(d);

            EmotionAnalysis ana = orchestrator.analyzeDiary(d, "RULE_V1");
            // 演示初始化阶段直接走受控本地生成（若已配置 DeepSeek，后续用户新写或点重新生成即可实时调用 DeepSeek）
            orchestrator.generateSuggestion(d, ana, "seed-" + UUID.randomUUID(), true);
        }
    }

    private void seedDemoFeedbackIfEmpty(Long studentId) {
        Long cnt = feedbackMapper.selectCount(null);
        if (cnt != null && cnt > 0) return;
        UserFeedback fb = new UserFeedback();
        fb.setUserId(studentId);
        fb.setTargetType("SUGGESTION");
        fb.setRating(5);
        fb.setComment("结合历史日记给出的行动建议很温和具体，不会有说教感，情绪趋势图也很直观。");
        fb.setStatus("PENDING");
        fb.setCreatedAt(LocalDateTime.now());
        fb.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.insert(fb);
    }
}
