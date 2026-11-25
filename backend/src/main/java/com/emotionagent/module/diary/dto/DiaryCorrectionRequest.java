package com.emotionagent.module.diary.dto;

import lombok.Data;

@Data
public class DiaryCorrectionRequest {
    private String correctedEmotionTag;
    private String correctedPolarity;
}
