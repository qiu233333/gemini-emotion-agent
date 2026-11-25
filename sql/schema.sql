-- 基于大语言模型的情绪记录与管理智能体系统 —— 数据库建表脚本
-- MySQL 8.0+ / UTF8MB4

CREATE DATABASE IF NOT EXISTS `emotion_agent`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `emotion_agent`;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt加盐密码哈希',
  `nickname` VARCHAR(64) NOT NULL COMMENT '昵称',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `bio` VARCHAR(255) DEFAULT NULL COMMENT '个人简介',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: USER / ADMIN',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE / DISABLED',
  `ai_memory_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否开启历史记忆关联',
  `memory_window_days` INT NOT NULL DEFAULT 30 COMMENT '历史记忆窗口天数(7-30)',
  `daily_ai_limit` INT NOT NULL DEFAULT 30 COMMENT '每日AI调用预算上限',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 2. 情绪日记表
CREATE TABLE IF NOT EXISTS `emotion_diary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日记ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(120) NOT NULL COMMENT '日记标题',
  `content` TEXT NOT NULL COMMENT '日记正文',
  `entry_date` DATE NOT NULL COMMENT '记录日期',
  `self_emotion_tag` VARCHAR(32) DEFAULT NULL COMMENT '用户自评情绪标签',
  `corrected_emotion_tag` VARCHAR(32) DEFAULT NULL COMMENT '用户纠偏后的主情绪标签',
  `corrected_polarity` VARCHAR(32) DEFAULT NULL COMMENT '用户纠偏后的情绪极性',
  `content_version` INT NOT NULL DEFAULT 1 COMMENT '文本版本号(修改正文递增)',
  `ai_consent` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '用户是否授权AI处理本篇日记',
  `memory_included` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许纳入历史记忆检索',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_diary_user_date` (`user_id`, `entry_date`),
  KEY `idx_diary_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_diary_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪日记表';

-- 3. 情绪分析结果表
CREATE TABLE IF NOT EXISTS `emotion_analysis` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分析ID',
  `diary_id` BIGINT NOT NULL COMMENT '关联日记ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `content_version` INT NOT NULL COMMENT '对应的日记文本版本号',
  `analyzer_method` VARCHAR(32) NOT NULL COMMENT '分析方法: RULE_V1 / SNOWNLP / BERT',
  `polarity` VARCHAR(32) NOT NULL COMMENT '情绪极性: POSITIVE / NEGATIVE / NEUTRAL / MIXED / UNCERTAIN',
  `primary_emotion` VARCHAR(32) NOT NULL COMMENT '主情绪: JOY / ANXIETY / SADNESS / ANGER / CALM / MIXED / UNCERTAIN',
  `secondary_emotions` JSON DEFAULT NULL COMMENT '辅助情绪数组',
  `emotion_scores` JSON DEFAULT NULL COMMENT '各情绪维度得分明细',
  `intensity_score` INT NOT NULL DEFAULT 50 COMMENT '情绪强度辅助分(0-100,非医学量表)',
  `keywords` JSON DEFAULT NULL COMMENT '提取的关键词列表',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风险等级: NONE / LOW / HIGH',
  `risk_matched_rules` JSON DEFAULT NULL COMMENT '命中的风险规则名称列表',
  `explanation` TEXT DEFAULT NULL COMMENT '可解释性分析说明',
  `is_latest` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否为当前最新有效分析',
  `analyzed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_analysis_diary_latest` (`diary_id`, `is_latest`),
  KEY `idx_analysis_user_latest` (`user_id`, `is_latest`, `analyzed_at`),
  CONSTRAINT `fk_analysis_diary` FOREIGN KEY (`diary_id`) REFERENCES `emotion_diary` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_analysis_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪分析结果表';

-- 4. AI 建议记录表
CREATE TABLE IF NOT EXISTS `ai_suggestion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '建议ID',
  `diary_id` BIGINT NOT NULL COMMENT '关联日记ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `content_version` INT NOT NULL COMMENT '对应日记文本版本号',
  `analysis_id` BIGINT DEFAULT NULL COMMENT '关联分析ID',
  `request_id` VARCHAR(64) NOT NULL COMMENT '幂等请求ID',
  `used_history` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否结合了历史记忆摘要',
  `referenced_diary_ids` JSON DEFAULT NULL COMMENT '引用的历史日记编号列表',
  `model_name` VARCHAR(64) NOT NULL COMMENT '模型名称(如 deepseek-chat / rule-fallback)',
  `prompt_version` VARCHAR(32) NOT NULL COMMENT 'Prompt模板版本号',
  `status` VARCHAR(32) NOT NULL COMMENT '生成状态: SUCCESS / FALLBACK / HIGH_RISK_SAFE / FAILED',
  `empathy_text` TEXT DEFAULT NULL COMMENT '共情承接段落',
  `action_items` JSON DEFAULT NULL COMMENT '1-3条可执行日常建议',
  `disclaimer` VARCHAR(512) DEFAULT NULL COMMENT '非医疗定位提示',
  `validation_passed` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '输出安全校验是否通过',
  `validation_notes` VARCHAR(512) DEFAULT NULL COMMENT '校验备注',
  `agent_trace` JSON DEFAULT NULL COMMENT '轻量级智能体工具调用轨迹',
  `latency_ms` INT NOT NULL DEFAULT 0 COMMENT '耗时(ms)',
  `prompt_tokens` INT DEFAULT 0 COMMENT '输入Token数',
  `completion_tokens` INT DEFAULT 0 COMMENT '输出Token数',
  `is_latest` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否为最新建议',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_suggestion_request_id` (`request_id`),
  KEY `idx_suggestion_diary_latest` (`diary_id`, `is_latest`),
  KEY `idx_suggestion_user_created` (`user_id`, `created_at`),
  CONSTRAINT `fk_suggestion_diary` FOREIGN KEY (`diary_id`) REFERENCES `emotion_diary` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_suggestion_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI情绪调节建议记录表';

-- 5. AI 陪伴对话会话表 (进阶模块)
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(120) NOT NULL COMMENT '会话标题',
  `use_diary_memory` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否结合近期日记上下文',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_session_user` (`user_id`, `updated_at`),
  CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI陪伴对话会话表';

-- 6. AI 陪伴对话消息表 (进阶模块)
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` BIGINT NOT NULL COMMENT '所属会话ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `role` VARCHAR(16) NOT NULL COMMENT '角色: USER / ASSISTANT',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `detected_emotion` VARCHAR(32) DEFAULT NULL COMMENT '识别出的情绪标签',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '风险等级',
  `referenced_diary_ids` JSON DEFAULT NULL COMMENT '引用的历史日记编号',
  `model_name` VARCHAR(64) DEFAULT NULL COMMENT '模型名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS / FALLBACK / HIGH_RISK_SAFE',
  `latency_ms` INT DEFAULT 0 COMMENT '耗时(ms)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_msg_session` (`session_id`, `created_at`),
  KEY `idx_chat_msg_user` (`user_id`, `created_at`),
  CONSTRAINT `fk_chat_msg_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chat_msg_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI陪伴对话消息表';

-- 7. 周报/月报周期总结表 (进阶模块)
CREATE TABLE IF NOT EXISTS `periodic_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `report_type` VARCHAR(16) NOT NULL COMMENT '类型: WEEKLY / MONTHLY',
  `start_date` DATE NOT NULL COMMENT '统计开始日期',
  `end_date` DATE NOT NULL COMMENT '统计结束日期',
  `diary_count` INT NOT NULL DEFAULT 0 COMMENT '周期内日记条数',
  `stats_snapshot` JSON DEFAULT NULL COMMENT '周期统计快照',
  `summary_text` TEXT DEFAULT NULL COMMENT 'AI总结正文',
  `highlights` JSON DEFAULT NULL COMMENT '情绪亮点与波动观察',
  `suggestions` JSON DEFAULT NULL COMMENT '下阶段温和建议',
  `referenced_diary_ids` JSON DEFAULT NULL COMMENT '引用的日记编号',
  `model_name` VARCHAR(64) DEFAULT NULL COMMENT '生成模型',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态: SUCCESS / FALLBACK',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_user_period` (`user_id`, `report_type`, `start_date`),
  CONSTRAINT `fk_report_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪周报月报表';

-- 8. 风险规则与敏感词表
CREATE TABLE IF NOT EXISTS `risk_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `name` VARCHAR(64) NOT NULL COMMENT '规则名称',
  `pattern` VARCHAR(255) NOT NULL COMMENT '匹配词或正则表达式',
  `match_type` VARCHAR(16) NOT NULL DEFAULT 'KEYWORD' COMMENT '匹配类型: KEYWORD / REGEX',
  `severity` VARCHAR(16) NOT NULL DEFAULT 'HIGH' COMMENT '严重等级: LOW / HIGH',
  `category` VARCHAR(64) NOT NULL COMMENT '风险类别',
  `safe_template` TEXT NOT NULL COMMENT '命中时的安全支持性提示模板',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `updated_by` BIGINT DEFAULT NULL COMMENT '最后维护管理员ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_risk_rule_enabled_severity` (`enabled`, `severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险词与安全规则表';

-- 9. 用户反馈表
CREATE TABLE IF NOT EXISTS `user_feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  `user_id` BIGINT NOT NULL COMMENT '提交用户ID',
  `target_type` VARCHAR(32) NOT NULL COMMENT '反馈对象: ANALYSIS / SUGGESTION / CHAT / REPORT / SYSTEM',
  `target_id` BIGINT DEFAULT NULL COMMENT '对象ID',
  `rating` INT NOT NULL DEFAULT 5 COMMENT '满意度评分(1-5)',
  `comment` VARCHAR(1000) DEFAULT NULL COMMENT '具体意见',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING / RESOLVED',
  `admin_reply` VARCHAR(1000) DEFAULT NULL COMMENT '管理员回复',
  `handled_by` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_user` (`user_id`, `created_at`),
  KEY `idx_feedback_status` (`status`, `created_at`),
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表';

-- 10. 脱敏审计日志表
CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计ID',
  `actor_user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `actor_role` VARCHAR(20) DEFAULT NULL COMMENT '操作人角色',
  `action` VARCHAR(64) NOT NULL COMMENT '操作行为',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '资源类型',
  `resource_id` BIGINT DEFAULT NULL COMMENT '资源ID',
  `summary_masked` VARCHAR(512) DEFAULT NULL COMMENT '脱敏摘要(严禁记录私人日记正文)',
  `ip_address` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_created` (`created_at`),
  KEY `idx_audit_actor` (`actor_user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作审计日志表';
