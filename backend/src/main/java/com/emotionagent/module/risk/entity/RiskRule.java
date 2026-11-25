package com.emotionagent.module.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_rule")
public class RiskRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String pattern;
    private String matchType;
    private String severity;
    private String category;
    private String safeTemplate;
    private Boolean enabled;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
