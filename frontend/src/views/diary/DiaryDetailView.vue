<template>
  <div v-loading="loading">
    <div v-if="diary" class="detail-grid">
      <!-- 左侧：日记原文 + 结构化情绪分析 + 纠偏 -->
      <div class="left-col">
        <div class="page-card">
          <div class="detail-top">
            <div>
              <div class="meta-line">
                <el-tag size="small" effect="plain">{{ diary.entryDate }}</el-tag>
                <el-tag size="small" type="info" effect="plain">文本版本 v{{ diary.contentVersion }}</el-tag>
                <EmotionBadge :emotion="diary.effectiveEmotion" />
                <el-tag v-if="diary.correctedEmotionTag" size="small" type="warning">已由你纠偏</el-tag>
              </div>
              <h2 class="diary-title">{{ diary.title }}</h2>
            </div>
            <div style="display: flex; gap: 8px;">
              <el-button size="small" @click="router.push(`/diaries/${diary.id}/edit`)">编辑</el-button>
              <el-button size="small" @click="router.push('/diaries')">返回列表</el-button>
            </div>
          </div>

          <div class="diary-body">{{ diary.content }}</div>

          <div class="tag-compare">
            <div>
              <span class="muted-text">你的自评标签：</span>
              <EmotionBadge v-if="diary.selfEmotionTag" :emotion="diary.selfEmotionTag" />
              <span v-else class="muted-text">未填写</span>
            </div>
            <div>
              <span class="muted-text">机器分析结果：</span>
              <EmotionBadge v-if="diary.analysis" :emotion="diary.analysis.primaryEmotion" />
              <span v-else class="muted-text">未分析</span>
            </div>
            <div>
              <span class="muted-text">当前生效标签：</span>
              <EmotionBadge :emotion="diary.effectiveEmotion" />
            </div>
          </div>
        </div>

        <!-- 结构化 NLP 分析卡 -->
        <div class="page-card" style="margin-top: 18px;">
          <div class="card-head-row">
            <h3 class="section-title" style="margin: 0;">结构化情绪分析</h3>
            <div style="display: flex; gap: 8px; align-items: center;">
              <el-select v-model="reanalyzeMethod" size="small" style="width: 140px;">
                <el-option label="RULE_V1 (规则)" value="RULE_V1" />
                <el-option label="ML_TFIDF (对照)" value="ML_TFIDF" />
                <el-option label="SNOWNLP" value="SNOWNLP" />
                <el-option label="BERT" value="BERT" />
              </el-select>
              <el-button size="small" :loading="reanalyzing" @click="onReanalyze">重新分析</el-button>
            </div>
          </div>

          <el-alert
            v-if="diary.analysisStale"
            title="日记正文已修改，当前展示为旧版本分析或尚未分析，建议点击右上角“重新分析”。"
            type="warning"
            show-icon
            :closable="false"
            style="margin-bottom: 12px;"
          />

          <div v-if="diary.analysis">
            <div class="ana-summary">
              <div class="ana-chip">
                <div class="muted-text">情绪极性</div>
                <el-tag :type="getPolarityMeta(diary.analysis.polarity).type" style="margin-top: 4px;">
                  {{ getPolarityMeta(diary.analysis.polarity).label }} ({{ diary.analysis.polarity }})
                </el-tag>
              </div>
              <div class="ana-chip">
                <div class="muted-text">主情绪 / 辅助情绪</div>
                <div style="margin-top: 4px; display: flex; gap: 6px; flex-wrap: wrap;">
                  <EmotionBadge :emotion="diary.analysis.primaryEmotion" />
                  <EmotionBadge
                    v-for="se in (diary.analysis.secondaryEmotions || [])"
                    :key="se"
                    :emotion="se"
                  />
                </div>
              </div>
              <div class="ana-chip">
                <div class="muted-text">情绪强度辅助分（非医学量表）</div>
                <el-progress
                  :percentage="diary.analysis.intensityScore"
                  :stroke-width="10"
                  color="#2b6e6c"
                  style="margin-top: 8px;"
                />
              </div>
            </div>

            <div class="score-bars" v-if="diary.analysis.emotionScores">
              <div class="muted-text" style="margin-bottom: 8px;">各维度规则/模型得分分布（方法：{{ diary.analysis.analyzerMethod }}）</div>
              <div class="score-grid">
                <div v-for="(val, key) in diary.analysis.emotionScores" :key="key" class="score-row">
                  <span class="score-name">{{ getEmotionMeta(key).icon }} {{ getEmotionMeta(key).label }}</span>
                  <div class="score-bar-track">
                    <div
                      class="score-bar-fill"
                      :style="{
                        width: Math.min(100, Math.round((val / 4) * 100)) + '%',
                        backgroundColor: getEmotionMeta(key).color
                      }"
                    />
                  </div>
                  <span class="score-val">{{ val }}</span>
                </div>
              </div>
            </div>

            <div style="margin-top: 14px;">
              <span class="muted-text">关键词提取：</span>
              <el-tag
                v-for="kw in (diary.analysis.keywords || [])"
                :key="kw"
                size="small"
                style="margin-right: 6px; cursor: pointer;"
                @click="router.push({ path: '/diaries', query: { keyword: kw } })"
              >
                # {{ kw }}
              </el-tag>
              <span v-if="!diary.analysis.keywords?.length" class="muted-text">无显著关键词</span>
            </div>

            <div class="explain-box">
              <strong>可解释性依据：</strong>{{ diary.analysis.explanation }}
            </div>

            <!-- 用户手动纠偏区 -->
            <div class="correct-box">
              <div class="correct-title">觉得机器判断不够贴切？你可以手动纠正标签（尊重个人主观感受）：</div>
              <div class="correct-row">
                <el-select v-model="correctForm.correctedEmotionTag" placeholder="选择你的主情绪" size="small" style="width: 150px;">
                  <el-option v-for="opt in EMOTION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
                <el-select v-model="correctForm.correctedPolarity" placeholder="选择情绪极性" size="small" style="width: 130px;">
                  <el-option label="积极 (POSITIVE)" value="POSITIVE" />
                  <el-option label="消极 (NEGATIVE)" value="NEGATIVE" />
                  <el-option label="中性 (NEUTRAL)" value="NEUTRAL" />
                  <el-option label="混合 (MIXED)" value="MIXED" />
                </el-select>
                <el-button type="primary" plain size="small" @click="onCorrect">保存纠偏</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else description="尚未执行情绪分析" :image-size="60" />
        </div>
      </div>

      <!-- 右侧：轻量级智能体 & DeepSeek 温和建议 + 追溯 + 轨迹 + 反馈 -->
      <div class="right-col">
        <div class="page-card">
          <div class="card-head-row">
            <h3 class="section-title" style="margin: 0;">智能体温和调节建议</h3>
            <el-dropdown @command="onRegenerate">
              <el-button size="small" type="primary" plain :loading="regenerating">
                重新生成 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="default">按当前记忆设置重新生成</el-dropdown-item>
                  <el-dropdown-item command="with_history">消融对比：强制结合近期历史摘要</el-dropdown-item>
                  <el-dropdown-item command="no_history">消融对比：仅依据当前日记（无历史）</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div v-if="diary.suggestion">
            <!-- 高风险危机短路保护卡片 -->
            <div v-if="diary.suggestion.status === 'HIGH_RISK_SAFE'" class="crisis-card">
              <div class="crisis-title">❤️ 高风险安全支持提醒</div>
              <p class="crisis-text">{{ diary.suggestion.empathyText }}</p>
              <ul class="action-list">
                <li v-for="(act, idx) in (diary.suggestion.actionItems || [])" :key="idx">{{ act }}</li>
              </ul>
              <div class="hotline-bar">
                全国统一心理援助热线：<strong>12356</strong> ｜ 急救/报警：<strong>120 / 110</strong> ｜ 学校心理健康中心
              </div>
            </div>

            <!-- 正常/兜底温和建议卡片 -->
            <div v-else>
              <div class="sug-meta">
                <el-tag size="small" :type="diary.suggestion.status === 'SUCCESS' ? 'success' : 'info'">
                  {{ diary.suggestion.status === 'SUCCESS' ? `DeepSeek (${diary.suggestion.modelName})` : '受控温和兜底模式' }}
                </el-tag>
                <el-tag size="small" :type="diary.suggestion.usedHistory ? 'success' : 'info'" effect="plain">
                  {{ diary.suggestion.usedHistory ? '已结合近期历史记忆' : '仅基于当前日记' }}
                </el-tag>
                <span class="muted-text">耗时 {{ diary.suggestion.latencyMs }}ms · Prompt {{ diary.suggestion.promptVersion }}</span>
              </div>

              <div class="empathy-block">
                <div class="block-label">温和共情承接</div>
                <p class="empathy-text">{{ diary.suggestion.empathyText }}</p>
              </div>

              <div class="actions-block">
                <div class="block-label">可选日常小行动（1–3 条）</div>
                <div v-for="(act, idx) in (diary.suggestion.actionItems || [])" :key="idx" class="action-card">
                  <span class="act-num">{{ idx + 1 }}</span>
                  <span>{{ act }}</span>
                </div>
              </div>

              <!-- 可追溯引用的历史日记 -->
              <div v-if="diary.suggestion.referencedDiaries?.length" class="ref-block">
                <div class="block-label">可追溯历史记忆来源（点击回看原文）</div>
                <div class="ref-list">
                  <el-link
                    v-for="rd in diary.suggestion.referencedDiaries"
                    :key="rd.id"
                    type="primary"
                    @click="router.push(`/diaries/${rd.id}`)"
                  >
                    日记 #{{ rd.id }} · {{ rd.entryDate }} 《{{ rd.title }}》
                  </el-link>
                </div>
              </div>

              <div class="disclaimer-bar" style="margin-top: 14px; font-size: 12px;">
                {{ diary.suggestion.disclaimer }}
              </div>
            </div>

            <!-- 轻量级智能体工具调用轨迹（答辩亮点展示） -->
            <el-collapse style="margin-top: 16px;">
              <el-collapse-item title="查看轻量级智能体工具编排轨迹 (Agent Trace)" name="trace">
                <el-timeline>
                  <el-timeline-item
                    v-for="(t, i) in (diary.suggestion.agentTrace || [])"
                    :key="i"
                    type="primary"
                    :timestamp="`Step ${t.step || i + 1}`"
                  >
                    <strong>{{ t.tool }}</strong>
                    <pre class="trace-json">{{ JSON.stringify(t, null, 2) }}</pre>
                  </el-timeline-item>
                </el-timeline>
              </el-collapse-item>
            </el-collapse>

            <!-- 快捷评价反馈 -->
            <div class="feedback-box">
              <div class="block-label">这条分析与建议对你有帮助吗？</div>
              <el-rate v-model="fbForm.rating" show-text :texts="['较差', '一般', '还行', '有帮助', '很有帮助']" />
              <div style="display: flex; gap: 8px; margin-top: 8px;">
                <el-input v-model="fbForm.comment" size="small" placeholder="可选：写下你对本次建议相关性或语气的评价" />
                <el-button size="small" type="primary" plain @click="onSubmitFeedback">提交反馈</el-button>
              </div>
            </div>
          </div>

          <el-empty v-else description="暂未生成 AI 建议">
            <el-button type="primary" @click="onRegenerate('default')">立即生成建议</el-button>
          </el-empty>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import EmotionBadge from '@/components/EmotionBadge.vue'
import { EMOTION_OPTIONS, getEmotionMeta, getPolarityMeta } from '@/utils/emotions'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const reanalyzing = ref(false)
const regenerating = ref(false)
const diary = ref(null)
const reanalyzeMethod = ref('RULE_V1')

const correctForm = reactive({
  correctedEmotionTag: '',
  correctedPolarity: ''
})

const fbForm = reactive({
  rating: 5,
  comment: ''
})

async function loadDiary() {
  loading.value = true
  try {
    const res = await api.getDiary(route.params.id)
    diary.value = res.data
    correctForm.correctedEmotionTag = dOr(res.data.correctedEmotionTag, res.data.analysis?.primaryEmotion)
    correctForm.correctedPolarity = dOr(res.data.correctedPolarity, res.data.analysis?.polarity)
    reanalyzeMethod.value = res.data.analysis?.analyzerMethod || 'RULE_V1'
  } finally {
    loading.value = false
  }
}

function dOr(a, b) {
  return a || b || ''
}

async function onCorrect() {
  const res = await api.correctDiary(diary.value.id, correctForm)
  diary.value = res.data
  ElMessage.success('已保存情绪标签纠偏')
}

async function onReanalyze() {
  reanalyzing.value = true
  try {
    const res = await api.reanalyzeDiary(diary.value.id, reanalyzeMethod.value)
    diary.value = res.data
    ElMessage.success(`已使用 ${res.data.analysis?.analyzerMethod} 完成重新分析`)
  } finally {
    reanalyzing.value = false
  }
}

async function onRegenerate(cmd) {
  regenerating.value = true
  try {
    const params = { requestId: `regen-${Date.now()}-${Math.random().toString(36).slice(2, 7)}` }
    if (cmd === 'with_history') params.useHistoryOverride = true
    if (cmd === 'no_history') params.useHistoryOverride = false
    const res = await api.regenerateSuggestion(diary.value.id, params)
    diary.value = res.data
    ElMessage.success('已生成新建议')
  } finally {
    regenerating.value = false
  }
}

async function onSubmitFeedback() {
  await api.submitFeedback({
    targetType: 'SUGGESTION',
    targetId: diary.value.suggestion?.id,
    rating: fbForm.rating,
    comment: fbForm.comment
  })
  ElMessage.success('感谢反馈，已提交至系统用于持续优化')
  fbForm.comment = ''
}

watch(() => route.params.id, () => {
  if (route.params.id) loadDiary()
})

onMounted(loadDiary)
</script>

<style scoped>
.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.left-col, .right-col {
  min-width: 0;
}
.trace-json {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--bg-page);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  max-width: 100%;
  overflow-x: auto;
}
.detail-top, .card-head-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}
.meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.diary-title {
  margin: 0;
  font-size: 20px;
}
.diary-body {
  font-size: 15px;
  line-height: 1.85;
  color: #243332;
  white-space: pre-wrap;
  padding: 14px 0;
  border-top: 1px solid #f0f4f2;
  border-bottom: 1px solid #f0f4f2;
}
.tag-compare {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding-top: 12px;
  align-items: center;
}
.ana-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.ana-chip {
  background: var(--bg-page);
  padding: 12px;
  border-radius: 10px;
}
.score-bars {
  background: #fafcfb;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 12px 14px;
}
.score-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.score-row {
  display: grid;
  grid-template-columns: 78px 1fr 42px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.score-bar-track {
  height: 8px;
  background: #e9efed;
  border-radius: 99px;
  overflow: hidden;
}
.score-bar-fill {
  height: 100%;
  border-radius: 99px;
  transition: width 0.3s ease;
}
.score-val {
  text-align: right;
  font-family: monospace;
  color: var(--text-secondary);
}
.explain-box {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f5f9f8;
  font-size: 13px;
  line-height: 1.6;
  color: #3a5250;
}
.correct-box {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--border-soft);
}
.correct-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.correct-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.sug-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.block-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}
.empathy-block {
  background: linear-gradient(180deg, #f3faf9 0%, #ffffff 100%);
  border: 1px solid #d7eae8;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.empathy-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
}
.action-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: var(--bg-page);
  border: 1px solid var(--border-soft);
  padding: 10px 12px;
  border-radius: 10px;
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.55;
}
.act-num {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}
.ref-block {
  margin-top: 14px;
  padding: 12px;
  background: #f9fbfa;
  border-radius: 10px;
  border: 1px solid var(--border-soft);
}
.ref-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.crisis-card {
  background: #fff5f5;
  border: 1px solid #f5b8b8;
  border-radius: 12px;
  padding: 16px;
}
.crisis-title {
  font-size: 16px;
  font-weight: 700;
  color: #b42318;
  margin-bottom: 8px;
}
.crisis-text {
  font-size: 14px;
  line-height: 1.75;
  color: #5c1914;
}
.action-list {
  margin: 10px 0;
  padding-left: 18px;
  color: #5c1914;
  line-height: 1.7;
  font-size: 14px;
}
.hotline-bar {
  background: #fee4e2;
  color: #912018;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.feedback-box {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--border-soft);
}
@media (max-width: 1024px) {
  .detail-grid { grid-template-columns: 1fr; }
  .ana-summary { grid-template-columns: 1fr; }
}
</style>
