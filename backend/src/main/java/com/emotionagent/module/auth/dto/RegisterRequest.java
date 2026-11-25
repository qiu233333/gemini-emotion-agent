package com.emotionagent.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度需在3-32个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64个字符之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 32, message = "昵称长度需在1-32个字符之间")
    private String nickname;

    private String email;
}
