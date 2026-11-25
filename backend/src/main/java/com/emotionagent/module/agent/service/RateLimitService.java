package com.emotionagent.module.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.common.BusinessException;
import com.emotionagent.config.AppProperties;
import com.emotionagent.module.auth.entity.SysUser;
import com.emotionagent.module.auth.mapper.SysUserMapper;
import com.emotionagent.module.diary.entity.AiSuggestion;
import com.emotionagent.module.diary.mapper.AiSuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final AppProperties appProperties;
    private final SysUserMapper sysUserMapper;
    private final AiSuggestionMapper aiSuggestionMapper;

    private final ConcurrentHashMap<Long, Deque<Long>> minuteBuckets = new ConcurrentHashMap<>();

    public void checkAiQuota(Long userId) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        int maxPerMin = appProperties.getAgent().getRateLimitPerMinute();

        Deque<Long> timestamps = minuteBuckets.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerMin) {
                throw new BusinessException(429, "AI 调用过于频繁，请稍后再试（每分钟上限 " + maxPerMin + " 次）");
            }
            timestamps.addLast(now);
        }

        SysUser user = sysUserMapper.selectById(userId);
        int dailyLimit = (user != null && user.getDailyAiLimit() != null) ? user.getDailyAiLimit() : 30;
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Long usedToday = aiSuggestionMapper.selectCount(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getUserId, userId)
                .ge(AiSuggestion::getCreatedAt, startOfDay));
        if (usedToday != null && usedToday >= dailyLimit) {
            throw new BusinessException(429, "今日 AI 建议调用已达上限（" + dailyLimit + " 次），基础情绪分析仍可正常使用");
        }
    }
}
