<template>
  <div>
    <div class="page-card">
      <h3 class="section-title">中文情感分析多算法对照实验台</h3>
      <div class="muted-text" style="margin-bottom: 12px;">
        输入任意中文情绪短文本，同时调用 Java 规则基线引擎（RULE_V1）与 Python 扩展服务（SNOWNLP、独立训练集拟合的 ML_TFIDF），用于答辩演示否定、转折、混合情绪与风险识别能力。
      </div>

      <el-input
        v-model="sampleText"
        type="textarea"
        :rows="3"
        placeholder="输入测试文本，例如：临近答辩有点担心讲不好，但看到系统成形仍然期待展示成果。"
      />

      <div class="sample-chips">
        <span class="muted-text">开题报告专项难例：</span>
        <el-tag
          v-for="s in presetCases"
          :key="s.label"
          class="chip"
          effect="plain"
          @click="sampleText = s.text"
        >
          {{ s.label }}
        </el-tag>
      </div>

      <div style="margin-top: 14px;">
        <el-button type="primary" :loading="running" @click="runCompare">
          执行三模型对照分析
        </el-button>
      </div>
    </div>

    <div v-if="results.length" class="compare-grid">
      <div v-for="item in results" :key="item.method" class="page-card">
        <div class="cmp-head">
          <strong>{{ item.title }}</strong>
          <el-tag size="small" effect="plain">{{ item.data.analysis.analyzerMethod }}</el-tag>
        </div>

        <div class="cmp-row">
          <span class="muted-text">极性判断：</span>
          <el-tag size="small" :type="getPolarityMeta(item.data.analysis.polarity).type">
            {{ getPolarityMeta(item.data.analysis.polarity).label }} ({{ item.data.analysis.polarity }})
          </el-tag>
        </div>

        <div class="cmp-row">
          <span class="muted-text">主情绪：</span>
          <EmotionBadge :emotion="item.data.analysis.primaryEmotion" />
          <EmotionBadge
            v-for="se in (item.data.analysis.secondaryEmotions || [])"
            :key="se"
            :emotion="se"
          />
        </div>

        <div class="cmp-row">
          <span class="muted-text">强度辅助分：</span>
          <strong>{{ item.data.analysis.intensityScore }} / 100</strong>
        </div>

        <div class="cmp-row">
          <span class="muted-text">风险检测：</span>
          <el-tag size="small" :type="item.data.risk.level === 'HIGH' ? 'danger' : (item.data.risk.level === 'LOW' ? 'warning' : 'success')">
            {{ item.data.risk.level }}
          </el-tag>
        </div>

        <div class="cmp-row" style="align-items: flex-start;">
          <span class="muted-text">关键词：</span>
          <div style="display: flex; gap: 4px; flex-wrap: wrap;">
            <el-tag v-for="kw in (item.data.analysis.keywords || [])" :key="kw" size="small" effect="plain">
              {{ kw }}
            </el-tag>
            <span v-if="!item.data.analysis.keywords?.length" class="muted-text">无</span>
          </div>
        </div>

        <div class="cmp-explain">{{ item.data.analysis.explanation }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import EmotionBadge from '@/components/EmotionBadge.vue'
import { getPolarityMeta } from '@/utils/emotions'

const sampleText = ref('临近毕业答辩有点担心自己讲不好，但是看到系统已经成形仍然期待展示成果。')
const running = ref(false)
const results = ref([])

const presetCases = [
  { label: '否定难例：并不开心', text: '虽然考完了试，但我今天回来之后其实并不开心，感觉挺失落的。' },
  { label: '转折混合：担心但期待', text: '临近毕业答辩有点担心自己讲不好，但是看到系统已经成形仍然期待展示成果。' },
  { label: '反讽语境：呵呵加班', text: '呵呵，太棒了又被临时通知通宵改毫无意义的格式，真是瞎折腾。' },
  { label: '无情绪客观句', text: '上午去教学楼上了两节课，中午在二食堂吃了拉面，下午回宿舍洗衣服。' },
  { label: '危机安全检测测试', text: '最近太痛苦了，觉得活不下去，产生了轻生的念头。' }
]

async function runCompare() {
  if (!sampleText.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  running.value = true
  try {
    const [r1, r2, r3] = await Promise.all([
      api.nlpAnalyze({ text: sampleText.value, method: 'RULE_V1' }),
      api.nlpAnalyze({ text: sampleText.value, method: 'SNOWNLP' }),
      api.nlpAnalyze({ text: sampleText.value, method: 'ML_TFIDF' })
    ])
    results.value = [
      { method: 'RULE_V1', title: '1. 中文规则基线引擎', data: r1.data },
      { method: 'SNOWNLP', title: '2. SnowNLP 情感模型', data: r2.data },
      { method: 'ML_TFIDF', title: '3. TF-IDF 监督对照模型', data: r3.data }
    ]
  } finally {
    running.value = false
  }
}
</script>

<style scoped>
.sample-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.chip {
  cursor: pointer;
}
.compare-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 18px;
}
.cmp-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--border-soft);
}
.cmp-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  flex-wrap: wrap;
}
.cmp-explain {
  margin-top: 12px;
  padding: 10px;
  border-radius: 8px;
  background: var(--bg-page);
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}
@media (max-width: 1000px) {
  .compare-grid { grid-template-columns: 1fr; }
}
</style>
