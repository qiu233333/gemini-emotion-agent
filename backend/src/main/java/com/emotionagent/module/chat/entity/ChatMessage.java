package com.emotionagent.module.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long userId;
    private String role; // USER / ASSISTANT
    private String content;
    private String detectedEmotion;
    private String riskLevel;
    private String referencedDiaryIds; // JSON array
    private String modelName;
    private String status;
    private Integer latencyMs;
    private LocalDateTime createdAt;
}
