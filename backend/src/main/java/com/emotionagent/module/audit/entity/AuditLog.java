package com.emotionagent.module.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long actorUserId;
    private String actorRole;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String summaryMasked;
    private String ipAddress;
    private LocalDateTime createdAt;
}
