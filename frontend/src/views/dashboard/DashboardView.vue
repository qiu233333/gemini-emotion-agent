<template>
  <div class="dashboard">
    <div class="disclaimer-bar" style="margin-bottom: 18px;">
      <el-icon style="margin-top: 2px;"><InfoFilled /></el-icon>
      <span>
        本系统为日常情绪记录、自我理解与非医疗辅助工具，不提供心理疾病诊断或治疗。所有日记归属于个人隐私空间，历史记忆开关可随时在设置中关闭或清除。
      </span>
    </div>

    <div class="kpi-grid">
      <div class="page-card kpi-card">
        <div class="kpi-label">累计情绪日记</div>
        <div class="kpi-value">{{ stats.totalAllTime || 0 }} <span class="unit">篇</span></div>
        <div class="kpi-sub">近 30 天记录 {{ stats.recordCount || 0 }} 篇</div>
      </div>
      <div class="page-card kpi-card">
        <div class="kpi-label">近 30 天记录天数</div>
        <div class="kpi-value">{{ stats.recordedDays || 0 }} <span class="unit">天</span></div>
        <div class="kpi-sub">{{ stats.startDate }} ~ {{ stats.endDate }}</div>
      </div>
      <div class="page-card kpi-card">
        <div class="kpi-label">近期主导情绪</div>
        <div class="kpi-value" style="margin-top: 8px;">
          <EmotionBadge :emotion="dominantEmotion" />
        </div>
        <div class="kpi-sub">支持在日记详情中手动纠偏</div>
      </div>
      <div class="page-card kpi-card">
        <div class="kpi-label">智能体历史记忆</div>
        <div class="kpi-value" style="font-size: 20px; margin-top: 8px;">
          {{ auth.user?.aiMemoryEnabled ? `已开启 (${auth.user?.memoryWindowDays || 30}天)` : '已关闭' }}
        </div>
        <div class="kpi-sub">
          <el-link type="primary" :underline="false" @click="router.push('/profile')">调整隐私设置 →</el-link>
        </div>
      </div>
    </div>

    <div class="main-grid">
      <div class="page-card">
        <div class="card-header">
          <h3 class="section-title" style="margin: 0;">近 7 天情绪倾向与强度走势</h3>
          <el-button link type="primary" @click="router.push('/stats')">查看完整统计与日历 →</el-button>
        </div>
        <div class="muted-text" style="margin-bottom: 10px;">
          统计口径：缺失日期按无记录留空（不填充为中性情绪）；分值为同口径非医学辅助展示。
        </div>
        <div ref="chartRef" style="width: 100%; height: 280px;" />
      </div>

      <div class="page-card quick-panel">
        <h3 class="section-title">快速入口</h3>
        <div class="quick-actions">
          <div class="qa-card" @click="router.push('/diaries/new')">
            <div class="qa-title">✍️ 写一篇情绪日记</div>
            <div class="qa-desc">提交后自动执行中文 NLP 情绪分析并生成可追溯 DeepSeek 调节建议</div>
          </div>
          <div class="qa-card" @click="router.push('/chat')">
            <div class="qa-title">💬 开启 AI 陪伴对话</div>
            <div class="qa-desc">结合近期情绪记忆进行多轮温和交流，梳理当下压力</div>
          </div>
          <div class="qa-card" @click="router.push('/reports')">
            <div class="qa-title">📑 生成情绪周报 / 月报</div>
            <div class="qa-desc">一键汇总周期情绪波动、高频词与下阶段温和建议</div>
          </div>
          <div class="qa-card" @click="router.push('/nlp-lab')">
            <div class="qa-title">🧪 NLP 算法对照实验台</div>
            <div class="qa-desc">实时对比规则法基线、SnowNLP 与监督学习模型识别结果</div>
          </div>
        </div>
      </div>
    </div>

    <div class="page-card" style="margin-top: 20px;">
      <div class="card-header">
        <h3 class="section-title" style="margin: 0;">近期情绪日记</h3>
        <el-button link type="primary" @click="router.push('/diaries')">查看全部日记 →</el-button>
      </div>

      <el-empty v-if="!recentDiaries.length" description="还没有记录情绪日记，写下第一篇吧">
        <el-button type="primary" @click="router.push('/diaries/new')">立即写日记</el-button>
      </el-empty>

      <div v-else class="recent-grid">
        <div
          v-for="d in recentDiaries"
          :key="d.id"
          class="diary-mini"
          @click="router.push(`/diaries/${d.id}`)"
        >
          <div class="dm-top">
            <span class="dm-date">{{ d.entryDate }}</span>
            <EmotionBadge :emotion="d.effectiveEmotion" />
          </div>
          <div class="dm-title">{{ d.title }}</div>
          <div class="dm-snippet">{{ d.content }}</div>
          <div class="dm-tags">
            <el-tag
              v-for="kw in (d.analysis?.keywords || []).slice(0, 3)"
              :key="kw"
              size="small"
              effect="plain"
            >
              # {{ kw }}
            </el-tag>
            <el-tag v-if="d.suggestion?.usedHistory" size="small" type="success" effect="light">
              已关联历史记忆
            </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import EmotionBadge from '@/components/EmotionBadge.vue'
import { getEmotionMeta } from '@/utils/emotions'

const router = useRouter()
const auth = useAuthStore()
const stats = ref({})
const stats7 = ref({})
const recentDiaries = ref([])
const chartRef = ref()
let chartInst = null

const dominantEmotion = computed(() => {
  const dist = stats.value.emotionDistribution || {}
  const entries = Object.entries(dist)
  if (!entries.length) return 'UNCERTAIN'
  entries.sort((a, b) => b[1] - a[1])
  return entries[0][0]
})

function renderMiniChart() {
  if (!chartRef.value) return
  if (!chartInst) chartInst = echarts.init(chartRef.value)
  const d = stats7.value
  chartInst.setOption({
    tooltip: {
      trigger: 'axis',
      formatter(params) {
        const idx = params[0]?.dataIndex ?? 0
        const date = (d.dates || [])[idx]
        const cnt = (d.dailyCounts || [])[idx] || 0
        if (!cnt) return `${date}<br/>无记录（按缺失留空）`
        const emo = getEmotionMeta((d.dailyPrimaryEmotions || [])[idx])
        const inten = (d.avgIntensities || [])[idx]
        const val = (d.moodValenceScores || [])[idx]
        return `${date}（共 ${cnt} 篇）<br/>主导情绪：${emo.icon} ${emo.label}<br/>情绪倾向值：${val}<br/>辅助强度均值：${inten}/100`
      }
    },
    legend: { data: ['情绪倾向值', '辅助强度均值'], bottom: 0 },
    grid: { left: 46, right: 46, top: 38, bottom: 42 },
    xAxis: { type: 'category', data: (d.dates || []).map((s) => s.slice(5)) },
    yAxis: [
      { type: 'value', name: '倾向', min: -100, max: 100 },
      { type: 'value', name: '强度', min: 0, max: 100 }
    ],
    series: [
      {
        name: '情绪倾向值',
        type: 'line',
        connectNulls: false,
        smooth: true,
        symbolSize: 8,
        itemStyle: { color: '#2b6e6c' },
        areaStyle: { color: 'rgba(43, 110, 108, 0.12)' },
        data: d.moodValenceScores || []
      },
      {
        name: '辅助强度均值',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 18,
        itemStyle: { color: '#9cc8c5', borderRadius: [4, 4, 0, 0] },
        data: d.avgIntensities || []
      }
    ]
  })
}

async function loadData() {
  const [res30, res7, resList] = await Promise.all([
    api.statsDashboard(30),
    api.statsDashboard(7),
    api.listDiaries({ page: 1, size: 4 })
  ])
  stats.value = res30.data || {}
  stats7.value = res7.data || {}
  recentDiaries.value = resList.data?.records || []
  await nextTick()
  renderMiniChart()
}

function handleResize() {
  chartInst?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chartInst?.dispose()
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.kpi-card {
  padding: 18px 20px;
}
.kpi-label {
  font-size: 13px;
  color: var(--text-secondary);
}
.kpi-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-main);
  margin: 6px 0;
}
.kpi-value .unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}
.kpi-sub {
  font-size: 12px;
  color: #7d8f8d;
}
.main-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.qa-card {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--bg-page);
  border: 1px solid var(--border-soft);
  cursor: pointer;
  transition: all 0.16s ease;
}
.qa-card:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  transform: translateY(-1px);
}
.qa-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 3px;
}
.qa-desc {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.45;
}
.recent-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}
.diary-mini {
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--border-soft);
  cursor: pointer;
  transition: all 0.15s ease;
}
.diary-mini:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 4px 14px rgba(43, 110, 108, 0.08);
}
.dm-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.dm-date {
  font-size: 12px;
  color: var(--text-secondary);
}
.dm-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}
.dm-snippet {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 10px;
}
.dm-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
@media (max-width: 1024px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .main-grid { grid-template-columns: 1fr; }
  .recent-grid { grid-template-columns: 1fr; }
}
</style>
