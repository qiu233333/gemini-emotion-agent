package com.emotionagent.module.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileDto {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String bio;
    private String role;
    private String status;
    private Boolean aiMemoryEnabled;
    private Integer memoryWindowDays;
    private Integer dailyAiLimit;
    private LocalDateTime createdAt;
}
