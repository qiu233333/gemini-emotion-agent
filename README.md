# 心迹 · 基于大语言模型的情绪记录与管理智能体系统

本科毕业设计完整实现项目：《基于大语言模型的情绪记录与管理智能体系统的设计与实现》。

> **非医疗定位声明**：本系统定位为大学生日常情绪记录、自我理解、趋势回顾与温和陪伴辅助工具，不提供心理疾病诊断、医学量表评估或临床治疗。遇高风险危机表述时，系统优先触发固定支持性安全提示并引导联系现实求助资源（亲友、辅导员、学校心理健康中心、全国心理援助热线 `12356`、`120/110`）。

---

## 一、项目目录结构

```text
毕设/
├── backend/              # Spring Boot 3 (Java 17) + MyBatis-Plus + Spring Security + JWT + Jieba 规则 NLP + DeepSeek 智能体
├── frontend/             # Vue 3 + Vite + Element Plus + ECharts 用户端与隐私隔离管理后台
├── nlp-service/          # Python FastAPI 扩展对照服务（SnowNLP / 独立训练集 TF-IDF 监督模型 / 可选 BERT 接口）
├── sql/                  # MySQL 建表脚本 schema.sql 与初始风险规则 seed_data.sql
├── eval/                 # 论文第六章实验套件（344 条标注样本、NLP 对照实验、30 场景消融与风控脚本、20 并发压测）
├── docs/                 # 汇总测试与验收报告 TEST_REPORT.md
├── .env.example          # DeepSeek API Key 与数据库环境变量模板
├── start.sh / stop.sh    # 一键启动与停止脚本
└── README.md
```

---

## 二、快速启动与 DeepSeek 接入

### 1. 配置 DeepSeek API Key（可选，但建议答辩演示配置）
复制 `.env.example` 为 `.env`，填入你的 DeepSeek Key：

```bash
cp .env.example .env
# 编辑 .env，设置：
# DEEPSEEK_API_KEY=sk-你的DeepSeek密钥
```

- **已配置 `DEEPSEEK_API_KEY`**：日记情绪建议、AI 陪伴多轮对话与周报/月报均实时调用 DeepSeek (`https://api.deepseek.com/v1/chat/completions`, `deepseek-chat`)，并经过输出安全与格式校验器。
- **未配置或网络超时**：系统自动降级至上下文感知温和兜底策略，并保留前端重试入口，日记原文不受任何影响。

### 2. 一键启动全部服务

```bash
chmod +x start.sh stop.sh
./start.sh
```

访问地址：
- **Web 前端**：`http://127.0.0.1:5174`
- **Spring Boot 后端健康检查**：`http://127.0.0.1:8088/api/health`
- **Python NLP 对照服务健康检查**：`http://127.0.0.1:8001/health`

### 3. 默认演示账号（由 `DataInitializer` 幂等初始化）
| 角色 | 用户名 | 密码 | 说明 |
| :--- | :--- | :--- | :--- |
| 演示学生 | `student` | `Student@123` | 预置 10 篇近 14 天大学生情绪日记（含缺失日期、同日双记、否定与混合情绪） |
| 对比测试学生 | `student2` | `Student@123` | 用于跨用户越权隔离与消融实验 |
| 系统管理员 | `admin` | `Admin@123` | 进入管理后台（默认严格隔离，不展示用户私人日记正文） |

---

## 三、核心功能与亮点清单

1. **情绪日记全生命周期**：新增、编辑（正文修改递增 `content_version` 自动使旧分析失效）、删除（事务级联清理分析/建议）、分页筛选、自评标签与机器分析区分、手动情绪纠偏。
2. **中文情感分析与多模型对照**：
   - Java 规则基线引擎 `RULE_V1`：Jieba 分词 + 五维情绪词典 + 程度副词 + 否定翻转（如“并不开心”）+ 转折结构分句加权（如“有点担心但仍然期待”→ `MIXED`）+ 反讽启发式 + 证据不足 `UNCERTAIN` + 关键词提取与可读解释。
   - Python FastAPI 扩展服务：提供 `SNOWNLP`、`ML_TFIDF`（严格仅用 `train` 集训练）及可选 `BERT` 推理接口，可在前端 **NLP 算法对照台** 实时并排演示。
3. **轻量级智能体编排与安全边界**：
   - 工具白名单依次编排：`RiskDetector` → `EmotionAnalyzer` → 高风险短路保护 / `HistoryMemoryTool`（7–30 天受控检索）→ 防注入受控 Prompt → `DeepSeekLlmClient` → `OutputSafetyValidator`（禁医疗诊断词、过滤模型幻觉日记 ID）。
   - 前端日记详情页支持展开查看完整 **Agent Trace 调用轨迹** 与点击跳转到引用的历史日记原文。
4. **ECharts 统计与情绪日历**：近 7/30/90 天情绪倾向走势与强度柱状图（**缺失日期严格留空 `null`，不补中性**）、情绪分布环形图、高频词云与柱状图、点击图表下钻回看原日记、月度情绪日历。
5. **AI 陪伴多轮对话 & 周报/月报**：支持多会话管理、可选结合近期日记记忆、一键生成近 7 天周报与近 30 天月报。
6. **隐私隔离管理后台**：用户启停与每日 AI 预算管控、风险规则/正则动态维护、用户反馈处理、脱敏审计日志。

---

## 四、运行测试与论文第六章实验脚本

```bash
# 1. 运行后端全部 JUnit 5 单元与集成测试（含 IDOR 越权、高风险短路、删除联动、统计留空）
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd backend && mvn test

# 2. 在全部服务运行状态下，复现论文第六章三组实验并生成 Markdown 报告
nlp-service/.venv/bin/python eval/evaluate_nlp.py      # -> eval/nlp_eval_report.md
nlp-service/.venv/bin/python eval/evaluate_ablation.py # -> eval/ablation_report.md
nlp-service/.venv/bin/python eval/perf_benchmark.py    # -> eval/perf_report.md
```
