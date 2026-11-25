<template>
  <div>
    <div class="page-card head-bar">
      <div>
        <h3 class="section-title" style="margin: 0;">情绪周期周报与月报</h3>
        <div class="muted-text" style="margin-top: 4px;">
          自动汇总周期内的情绪分布、关键词与日记摘要，借助 DeepSeek 生成回顾总结与下阶段温和建议。
        </div>
      </div>
      <div style="display: flex; gap: 10px;">
        <el-button type="primary" :loading="generating === 'WEEKLY'" @click="onGenerate('WEEKLY')">
          生成近 7 天周报
        </el-button>
        <el-button type="primary" plain :loading="generating === 'MONTHLY'" @click="onGenerate('MONTHLY')">
          生成近 30 天月报
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="report-list">
      <el-empty v-if="!loading && !reports.length" description="暂无周期报告，点击上方按钮一键生成" />

      <div v-for="r in reports" :key="r.id" class="page-card report-card">
        <div class="rc-top">
          <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
            <el-tag :type="r.reportType === 'MONTHLY' ? 'warning' : 'success'">
              {{ r.reportType === 'MONTHLY' ? '月度情绪报告' : '周度情绪报告' }}
            </el-tag>
            <strong>{{ r.startDate }} 至 {{ r.endDate }}</strong>
            <span class="muted-text">共统计 {{ r.diaryCount }} 篇日记 · 模型：{{ r.modelName }}</span>
          </div>
          <el-popconfirm title="确定删除这份周期报告？" @confirm="onDelete(r.id)">
            <template #reference>
              <el-button size="small" type="danger" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </div>

        <p class="rc-summary">{{ r.summaryText }}</p>

        <div class="rc-grid">
          <div class="rc-box">
            <div class="rc-box-title">周期亮点与波动观察</div>
            <ul>
              <li v-for="(h, i) in (r.highlights || [])" :key="i">{{ h }}</li>
            </ul>
          </div>
          <div class="rc-box">
            <div class="rc-box-title">下阶段温和行动建议</div>
            <ul>
              <li v-for="(s, i) in (r.suggestions || [])" :key="i">{{ s }}</li>
            </ul>
          </div>
        </div>

        <div v-if="r.referencedDiaryIds?.length" class="rc-refs">
          <span class="muted-text">纳入汇总的日记：</span>
          <el-link
            v-for="did in r.referencedDiaryIds"
            :key="did"
            type="primary"
            style="margin-right: 8px; font-size: 13px;"
            @click="router.push(`/diaries/${did}`)"
          >
            日记 #{{ did }}
          </el-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'

const router = useRouter()
const loading = ref(false)
const generating = ref('')
const reports = ref([])

async function loadReports() {
  loading.value = true
  try {
    const res = await api.listReports()
    reports.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function onGenerate(type) {
  generating.value = type
  try {
    const res = await api.generateReport(type)
    reports.value.unshift(res.data)
    ElMessage.success(type === 'MONTHLY' ? '月报生成成功' : '周报生成成功')
  } finally {
    generating.value = ''
  }
}

async function onDelete(id) {
  await api.deleteReport(id)
  reports.value = reports.value.filter((r) => r.id !== id)
  ElMessage.success('报告已删除')
}

onMounted(loadReports)
</script>

<style scoped>
.head-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 18px;
}
.report-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.rc-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.rc-summary {
  background: var(--bg-soft);
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.75;
  margin: 0 0 14px;
}
.rc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.rc-box {
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 12px 16px;
}
.rc-box-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin-bottom: 8px;
}
.rc-box ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.75;
  color: #344544;
}
.rc-refs {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f0f4f2;
}
@media (max-width: 820px) {
  .rc-grid { grid-template-columns: 1fr; }
}
</style>
