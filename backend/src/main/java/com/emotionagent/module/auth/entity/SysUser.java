package com.emotionagent.module.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String email;
    private String avatar;
    private String bio;
    private String role;
    private String status;
    private Boolean aiMemoryEnabled;
    private Integer memoryWindowDays;
    private Integer dailyAiLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
