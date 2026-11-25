<template>
  <div>
    <div class="page-card toolbar">
      <div>
        <div class="section-title" style="margin: 0;">情绪数据统计与长期回顾</div>
        <div class="muted-text" style="margin-top: 4px;">
          统计区间：{{ stats.startDate }} 至 {{ stats.endDate }} · 共 {{ stats.recordCount || 0 }} 篇日记（覆盖 {{ stats.recordedDays || 0 }} 天）· 缺失日期留空不填充中性，点击数据点可回看当日日记
        </div>
      </div>
      <el-radio-group v-model="days" @change="loadStats">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
    </div>

    <div class="page-card" style="margin-top: 18px;">
      <h3 class="section-title">情绪倾向走势与强度柱状图（点击日期可回看原日记）</h3>
      <div ref="trendRef" style="width: 100%; height: 320px;" />
    </div>

    <div class="chart-row-2" style="margin-top: 18px;">
      <div class="page-card">
        <h3 class="section-title">情绪类型分布</h3>
        <div ref="pieRef" style="width: 100%; height: 300px;" />
      </div>
      <div class="page-card">
        <h3 class="section-title">高频情绪关键词（点击关键词检索日记）</h3>
        <div v-if="!stats.topKeywords?.length" class="muted-text" style="padding: 40px 0; text-align: center;">
          暂无关键词统计
        </div>
        <div v-else>
          <div class="word-cloud">
            <span
              v-for="(kw, idx) in stats.topKeywords"
              :key="kw.name"
              class="cloud-item"
              :style="cloudStyle(kw.value, idx)"
              @click="router.push({ path: '/diaries', query: { keyword: kw.name } })"
            >
              {{ kw.name }} <sup>{{ kw.value }}</sup>
            </span>
          </div>
          <div ref="kwRef" style="width: 100%; height: 190px; margin-top: 10px;" />
        </div>
      </div>
    </div>

    <!-- 情绪日历（进阶功能） -->
    <div class="page-card" style="margin-top: 18px;">
      <div class="cal-head">
        <h3 class="section-title" style="margin: 0;">月度情绪日历（{{ calendar.month || selectedMonth }}）</h3>
        <el-date-picker
          v-model="selectedMonth"
          type="month"
          value-format="YYYY-MM"
          style="width: 150px;"
          @change="loadCalendar"
        />
      </div>
      <div class="cal-weekdays">
        <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">周{{ w }}</span>
      </div>
      <div class="cal-grid">
        <div v-for="(cell, idx) in calendarCells" :key="idx" class="cal-cell" :class="{ empty: !cell }">
          <template v-if="cell">
            <div class="cal-day-num">{{ cell.day }}</div>
            <div v-if="cell.entries.length" class="cal-entries">
              <div
                v-for="e in cell.entries.slice(0, 2)"
                :key="e.id"
                class="cal-pill"
                :style="{ backgroundColor: getEmotionMeta(e.emotion).bg, color: getEmotionMeta(e.emotion).color }"
                @click="router.push(`/diaries/${e.id}`)"
              >
                {{ getEmotionMeta(e.emotion).icon }} {{ e.title }}
              </div>
              <div v-if="cell.entries.length > 2" class="muted-text" style="font-size: 11px;">
                +{{ cell.entries.length - 2 }} 篇
              </div>
            </div>
          </template>
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
import { getEmotionMeta } from '@/utils/emotions'

const router = useRouter()
const days = ref(30)
const stats = ref({})
const calendar = ref({ days: {} })
const selectedMonth = ref(new Date().toISOString().slice(0, 7))

const trendRef = ref()
const pieRef = ref()
const kwRef = ref()
let trendChart = null
let pieChart = null
let kwChart = null

const palette = ['#2b6e6c', '#3b82a6', '#d9822b', '#6b7aa1', '#2e9e6f', '#8a63b8', '#d45348']

function cloudStyle(val, idx) {
  const maxVal = Math.max(...(stats.value.topKeywords || [{ value: 1 }]).map((k) => k.value), 1)
  const size = 13 + Math.round((val / maxVal) * 11)
  return {
    fontSize: `${size}px`,
    color: palette[idx % palette.length],
    backgroundColor: `${palette[idx % palette.length]}14`
  }
}

const calendarCells = computed(() => {
  const ym = selectedMonth.value || new Date().toISOString().slice(0, 7)
  const [y, m] = ym.split('-').map(Number)
  const firstDay = new Date(y, m - 1, 1)
  const daysInMonth = new Date(y, m, 0).getDate()
  const startOffset = (firstDay.getDay() + 6) % 7 // 周一为 0

  const cells = []
  for (let i = 0; i < startOffset; i++) cells.push(null)
  for (let d = 1; d <= daysInMonth; d++) {
    const dateKey = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    cells.push({
      day: d,
      dateKey,
      entries: calendar.value.days?.[dateKey] || []
    })
  }
  return cells
})

function renderCharts() {
  const d = stats.value
  if (trendRef.value) {
    if (!trendChart) {
      trendChart = echarts.init(trendRef.value)
      trendChart.on('click', (params) => {
        const clickedDate = (stats.value.dates || [])[params.dataIndex]
        if (clickedDate) {
          router.push({ path: '/diaries', query: { date: clickedDate } })
        }
      })
    }
    trendChart.setOption({
      tooltip: {
        trigger: 'axis',
        formatter(params) {
          const idx = params[0]?.dataIndex ?? 0
          const date = (d.dates || [])[idx]
          const cnt = (d.dailyCounts || [])[idx] || 0
          if (!cnt) return `${date}<br/>无记录（按缺失留空）`
          const emo = getEmotionMeta((d.dailyPrimaryEmotions || [])[idx])
          return `${date}（当日 ${cnt} 篇日记）<br/>主导情绪：${emo.icon} ${emo.label}<br/>倾向值：${(d.moodValenceScores || [])[idx]}<br/>强度均值：${(d.avgIntensities || [])[idx]}/100<br/><small>点击可查看当日日记</small>`
        }
      },
      legend: { data: ['情绪倾向走势', '辅助强度均值'], bottom: 0 },
      grid: { left: 54, right: 54, top: 40, bottom: 44 },
      xAxis: { type: 'category', data: (d.dates || []).map((s) => s.slice(5)) },
      yAxis: [
        { type: 'value', name: '倾向(-100~100)', min: -100, max: 100 },
        { type: 'value', name: '强度(0~100)', min: 0, max: 100 }
      ],
      series: [
        {
          name: '情绪倾向走势',
          type: 'line',
          connectNulls: false,
          smooth: true,
          symbolSize: 8,
          itemStyle: { color: '#2b6e6c' },
          areaStyle: { color: 'rgba(43, 110, 108, 0.14)' },
          data: d.moodValenceScores || []
        },
        {
          name: '辅助强度均值',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 20,
          itemStyle: { color: '#9fc9c6', borderRadius: [4, 4, 0, 0] },
          data: d.avgIntensities || []
        }
      ]
    })
  }

  if (pieRef.value) {
    if (!pieChart) pieChart = echarts.init(pieRef.value)
    const pieData = Object.entries(d.emotionDistribution || {}).map(([k, v]) => ({
      name: `${getEmotionMeta(k).icon} ${getEmotionMeta(k).label}`,
      value: v,
      itemStyle: { color: getEmotionMeta(k).color }
    }))
    pieChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 篇 ({d}%)' },
      legend: { bottom: 0 },
      series: [
        {
          type: 'pie',
          radius: ['42%', '70%'],
          center: ['50%', '44%'],
          itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
          label: { show: true, formatter: '{b}\n{d}%' },
          data: pieData
        }
      ]
    })
  }

  if (kwRef.value && d.topKeywords?.length) {
    if (!kwChart) kwChart = echarts.init(kwRef.value)
    const top8 = d.topKeywords.slice(0, 8).reverse()
    kwChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 72, right: 20, top: 8, bottom: 20 },
      xAxis: { type: 'value', minInterval: 1 },
      yAxis: { type: 'category', data: top8.map((k) => k.name) },
      series: [
        {
          type: 'bar',
          barWidth: 14,
          itemStyle: { color: '#3b82a6', borderRadius: [0, 4, 4, 0] },
          data: top8.map((k) => k.value)
        }
      ]
    })
  }
}

async function loadStats() {
  const res = await api.statsDashboard(days.value)
  stats.value = res.data || {}
  await nextTick()
  renderCharts()
}

async function loadCalendar() {
  const res = await api.statsCalendar(selectedMonth.value)
  calendar.value = res.data || { days: {} }
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
  kwChart?.resize()
}

onMounted(() => {
  loadStats()
  loadCalendar()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
  kwChart?.dispose()
})
</script>

<style scoped>
.toolbar, .cal-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.chart-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.word-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
}
.cloud-item {
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.14s;
}
.cloud-item:hover {
  transform: scale(1.06);
}
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
  padding-bottom: 8px;
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 8px;
}
.cal-cell {
  min-width: 0;
  min-height: 86px;
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 8px;
  background: #fff;
  overflow: hidden;
}
.cal-cell.empty {
  background: transparent;
  border-color: transparent;
}
.cal-day-num {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.cal-entries {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.cal-pill {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}
@media (max-width: 960px) {
  .chart-row-2 { grid-template-columns: 1fr; }
}
</style>
