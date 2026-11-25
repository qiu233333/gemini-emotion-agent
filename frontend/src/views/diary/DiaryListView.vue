<template>
  <div>
    <div class="page-card filter-bar">
      <el-input
        v-model="filters.keyword"
        placeholder="搜索日记标题或正文关键词"
        clearable
        style="width: 240px;"
        @keyup.enter="loadList(1)"
      />
      <el-select v-model="filters.emotion" placeholder="按情绪筛选" clearable style="width: 150px;" @change="loadList(1)">
        <el-option v-for="opt in EMOTION_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 260px;"
        @change="loadList(1)"
      />
      <el-button type="primary" @click="loadList(1)">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <div style="flex: 1;" />
      <el-button type="primary" plain @click="router.push('/diaries/new')">
        <el-icon><Plus /></el-icon> 写新日记
      </el-button>
    </div>

    <div v-loading="loading" class="diary-list">
      <el-empty v-if="!loading && !records.length" description="暂无符合条件的情绪日记" />

      <div v-for="item in records" :key="item.id" class="page-card diary-item">
        <div class="di-header">
          <div class="di-left" @click="router.push(`/diaries/${item.id}`)">
            <span class="di-date">{{ item.entryDate }}</span>
            <h3 class="di-title">{{ item.title }}</h3>
            <EmotionBadge :emotion="item.effectiveEmotion" />
            <el-tag v-if="item.correctedEmotionTag" size="small" type="warning" effect="plain">
              已手动纠偏
            </el-tag>
            <el-tag v-if="item.analysis?.riskLevel === 'HIGH'" size="small" type="danger" effect="dark">
              高风险关怀提示
            </el-tag>
          </div>
          <div class="di-actions">
            <el-button size="small" @click="router.push(`/diaries/${item.id}`)">查看详情与建议</el-button>
            <el-button size="small" plain @click="router.push(`/diaries/${item.id}/edit`)">编辑</el-button>
            <el-popconfirm title="删除后将同步清除关联分析、建议与记忆，确认删除？" @confirm="onDelete(item.id)">
              <template #reference>
                <el-button size="small" type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>

        <p class="di-content" @click="router.push(`/diaries/${item.id}`)">{{ item.content }}</p>

        <div class="di-footer">
          <div class="di-tags">
            <span v-if="item.selfEmotionTag" class="muted-text">
              自评：{{ getEmotionMeta(item.selfEmotionTag).label }}
            </span>
            <span v-if="item.analysis" class="muted-text">
              · 模型({{ item.analysis.analyzerMethod }})：{{ getEmotionMeta(item.analysis.primaryEmotion).label }}（强度 {{ item.analysis.intensityScore }}/100）
            </span>
            <el-tag v-for="kw in (item.analysis?.keywords || [])" :key="kw" size="small" effect="plain">
              # {{ kw }}
            </el-tag>
          </div>
          <div class="di-meta muted-text">
            <span v-if="item.suggestion">
              建议状态：{{ statusText(item.suggestion.status) }}
              <template v-if="item.suggestion.usedHistory"> · 引用 {{ item.suggestion.referencedDiaryIds?.length || 0 }} 篇历史</template>
            </span>
            <span v-else>未启用 AI 处理</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import EmotionBadge from '@/components/EmotionBadge.vue'
import { EMOTION_OPTIONS, getEmotionMeta } from '@/utils/emotions'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const dateRange = ref(null)

const filters = reactive({
  keyword: '',
  emotion: ''
})

function statusText(st) {
  return {
    SUCCESS: 'DeepSeek 已生成',
    FALLBACK: '温和兜底已生成',
    HIGH_RISK_SAFE: '安全关怀提示',
    FAILED: '生成失败'
  }[st] || st
}

async function loadList(targetPage = page.value) {
  page.value = targetPage
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: pageSize,
      keyword: filters.keyword || undefined,
      emotion: filters.emotion || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }
    const res = await api.listDiaries(params)
    records.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.emotion = ''
  dateRange.value = null
  loadList(1)
}

async function onDelete(id) {
  await api.deleteDiary(id)
  ElMessage.success('日记及关联分析、记忆已同步删除')
  loadList(page.value)
}

onMounted(() => {
  if (route.query.date) {
    dateRange.value = [route.query.date, route.query.date]
  }
  if (route.query.keyword) {
    filters.keyword = String(route.query.keyword)
  }
  loadList(1)
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 18px;
}
.diary-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.diary-item {
  transition: border-color 0.15s ease;
}
.diary-item:hover {
  border-color: var(--el-color-primary-light-5);
}
.di-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.di-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  cursor: pointer;
}
.di-date {
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-soft);
  padding: 2px 8px;
  border-radius: 6px;
}
.di-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.di-actions {
  display: flex;
  gap: 8px;
}
.di-content {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.65;
  color: #344544;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.di-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f4f2;
}
.di-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}
</style>
