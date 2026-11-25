package com.emotionagent.module.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(min = 1, max = 32, message = "昵称长度需在1-32个字符之间")
    private String nickname;

    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    @Size(max = 255, message = "简介不能超过255个字符")
    private String bio;

    private Boolean aiMemoryEnabled;

    @Min(value = 7, message = "历史记忆窗口不能少于7天")
    @Max(value = 30, message = "历史记忆窗口不能超过30天")
    private Integer memoryWindowDays;

    private String oldPassword;

    @Size(min = 6, max = 64, message = "新密码长度需在6-64个字符之间")
    private String newPassword;
}
