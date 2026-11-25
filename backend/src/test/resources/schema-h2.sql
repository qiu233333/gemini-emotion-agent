CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  email VARCHAR(128),
  avatar VARCHAR(255),
  bio VARCHAR(255),
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  ai_memory_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  memory_window_days INT NOT NULL DEFAULT 30,
  daily_ai_limit INT NOT NULL DEFAULT 30,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emotion_diary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content CLOB NOT NULL,
  entry_date DATE NOT NULL,
  self_emotion_tag VARCHAR(32),
  corrected_emotion_tag VARCHAR(32),
  corrected_polarity VARCHAR(32),
  content_version INT NOT NULL DEFAULT 1,
  ai_consent BOOLEAN NOT NULL DEFAULT TRUE,
  memory_included BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS emotion_analysis (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  diary_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content_version INT NOT NULL,
  analyzer_method VARCHAR(32) NOT NULL,
  polarity VARCHAR(32) NOT NULL,
  primary_emotion VARCHAR(32) NOT NULL,
  secondary_emotions CLOB,
  emotion_scores CLOB,
  intensity_score INT NOT NULL DEFAULT 50,
  keywords CLOB,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'NONE',
  risk_matched_rules CLOB,
  explanation CLOB,
  is_latest BOOLEAN NOT NULL DEFAULT TRUE,
  analyzed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_suggestion (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  diary_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content_version INT NOT NULL,
  analysis_id BIGINT,
  request_id VARCHAR(64) NOT NULL UNIQUE,
  used_history BOOLEAN NOT NULL DEFAULT FALSE,
  referenced_diary_ids CLOB,
  model_name VARCHAR(64) NOT NULL,
  prompt_version VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  empathy_text CLOB,
  action_items CLOB,
  disclaimer VARCHAR(512),
  validation_passed BOOLEAN NOT NULL DEFAULT TRUE,
  validation_notes VARCHAR(512),
  agent_trace CLOB,
  latency_ms INT NOT NULL DEFAULT 0,
  prompt_tokens INT DEFAULT 0,
  completion_tokens INT DEFAULT 0,
  is_latest BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  use_diary_memory BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  content CLOB NOT NULL,
  detected_emotion VARCHAR(32),
  risk_level VARCHAR(16) NOT NULL DEFAULT 'NONE',
  referenced_diary_ids CLOB,
  model_name VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  latency_ms INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS periodic_report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  report_type VARCHAR(16) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  diary_count INT NOT NULL DEFAULT 0,
  stats_snapshot CLOB,
  summary_text CLOB,
  highlights CLOB,
  suggestions CLOB,
  referenced_diary_ids CLOB,
  model_name VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS risk_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(64) NOT NULL,
  pattern VARCHAR(255) NOT NULL,
  match_type VARCHAR(16) NOT NULL DEFAULT 'KEYWORD',
  severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
  category VARCHAR(64) NOT NULL,
  safe_template CLOB NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT,
  rating INT NOT NULL DEFAULT 5,
  comment VARCHAR(1000),
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  admin_reply VARCHAR(1000),
  handled_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor_user_id BIGINT,
  actor_role VARCHAR(20),
  action VARCHAR(64) NOT NULL,
  resource_type VARCHAR(32) NOT NULL,
  resource_id BIGINT,
  summary_masked VARCHAR(512),
  ip_address VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
