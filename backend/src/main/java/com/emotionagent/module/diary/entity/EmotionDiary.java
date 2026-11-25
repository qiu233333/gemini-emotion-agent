package com.emotionagent.module.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("emotion_diary")
public class EmotionDiary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private LocalDate entryDate;
    private String selfEmotionTag;
    private String correctedEmotionTag;
    private String correctedPolarity;
    private Integer contentVersion;
    private Boolean aiConsent;
    private Boolean memoryIncluded;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
