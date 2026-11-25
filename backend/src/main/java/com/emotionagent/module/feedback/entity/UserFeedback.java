package com.emotionagent.module.feedback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_feedback")
public class UserFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String targetType; // ANALYSIS / SUGGESTION / CHAT / REPORT / SYSTEM
    private Long targetId;
    private Integer rating;
    private String comment;
    private String status;     // PENDING / RESOLVED
    private String adminReply;
    private Long handledBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
