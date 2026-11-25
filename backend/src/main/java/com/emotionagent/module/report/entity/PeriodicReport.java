package com.emotionagent.module.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("periodic_report")
public class PeriodicReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String reportType; // WEEKLY / MONTHLY
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer diaryCount;
    private String statsSnapshot;      // JSON
    private String summaryText;
    private String highlights;         // JSON array
    private String suggestions;        // JSON array
    private String referencedDiaryIds; // JSON array
    private String modelName;
    private String status;
    private LocalDateTime createdAt;
}
