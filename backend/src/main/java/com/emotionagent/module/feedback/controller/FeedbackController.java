package com.emotionagent.module.feedback.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.emotionagent.common.ApiResponse;
import com.emotionagent.common.BusinessException;
import com.emotionagent.config.SecurityUtils;
import com.emotionagent.module.feedback.entity.UserFeedback;
import com.emotionagent.module.feedback.mapper.UserFeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final UserFeedbackMapper feedbackMapper;

    @PostMapping
    public ApiResponse<UserFeedback> submit(@RequestBody Map<String, Object> body) {
        String targetType = body.get("targetType") != null ? String.valueOf(body.get("targetType")).toUpperCase() : "SYSTEM";
        Long targetId = body.get("targetId") != null && !String.valueOf(body.get("targetId")).isBlank()
                ? Long.valueOf(String.valueOf(body.get("targetId"))) : null;
        int rating = body.get("rating") != null ? Integer.parseInt(String.valueOf(body.get("rating"))) : 5;
        String comment = body.get("comment") != null ? String.valueOf(body.get("comment")).trim() : "";

        if (rating < 1 || rating > 5) {
            throw new BusinessException("评分需在 1 到 5 之间");
        }
        UserFeedback fb = new UserFeedback();
        fb.setUserId(SecurityUtils.currentUserId());
        fb.setTargetType(targetType);
        fb.setTargetId(targetId);
        fb.setRating(rating);
        fb.setComment(comment.length() > 1000 ? comment.substring(0, 1000) : comment);
        fb.setStatus("PENDING");
        fb.setCreatedAt(LocalDateTime.now());
        fb.setUpdatedAt(LocalDateTime.now());
        feedbackMapper.insert(fb);
        return ApiResponse.ok("感谢你的反馈", fb);
    }

    @GetMapping("/mine")
    public ApiResponse<List<UserFeedback>> mine() {
        return ApiResponse.ok(feedbackMapper.selectList(new LambdaQueryWrapper<UserFeedback>()
                .eq(UserFeedback::getUserId, SecurityUtils.currentUserId())
                .orderByDesc(UserFeedback::getCreatedAt)));
    }
}
