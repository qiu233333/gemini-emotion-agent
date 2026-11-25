<template>
  <div class="editor-wrap">
    <div class="page-card">
      <div class="editor-head">
        <div>
          <h3 class="section-title" style="margin: 0;">
            {{ isEdit ? '编辑情绪日记' : '写下此刻的情绪与想法' }}
          </h3>
          <div class="muted-text" style="margin-top: 4px;">
            日记原文将先独立安全落库，即使远程大模型超时也不影响内容保存。
          </div>
        </div>
        <el-button @click="router.back()">返回</el-button>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" style="margin-top: 18px;">
        <div class="form-row-2">
          <el-form-item label="日记标题" prop="title">
            <el-input v-model="form.title" placeholder="例如：准备中期答辩的一天 / 操场散步后松了口气" maxlength="120" show-word-limit />
          </el-form-item>
          <el-form-item label="记录日期" prop="entryDate">
            <el-date-picker
              v-model="form.entryDate"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%;"
            />
          </el-form-item>
        </div>

        <el-form-item label="此刻主观自评情绪（选填，将与机器分析区分保留）">
          <div class="mood-selector">
            <button
              v-for="opt in EMOTION_OPTIONS"
              :key="opt.value"
              type="button"
              class="mood-chip"
              :class="{ selected: form.selfEmotionTag === opt.value }"
              @click="form.selfEmotionTag = form.selfEmotionTag === opt.value ? '' : opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </el-form-item>

        <el-form-item label="日记正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            maxlength="5000"
            show-word-limit
            placeholder="记录今天发生了什么、你的真实感受或正在困扰你的事……无需讲究文笔，像和自己对话一样即可。"
          />
        </el-form-item>

        <div class="prompt-templates" v-if="!isEdit">
          <span class="muted-text">写作灵感：</span>
          <el-tag
            v-for="tpl in quickTemplates"
            :key="tpl.title"
            class="tpl-tag"
            effect="plain"
            @click="applyTemplate(tpl)"
          >
            {{ tpl.title }}
          </el-tag>
        </div>

        <div class="ai-options-box">
          <div class="opt-item">
            <div>
              <div class="opt-title">授权系统执行 AI 情绪分析与温和建议</div>
              <div class="muted-text">关闭后仅保存私人原文，不调用外部大模型 API</div>
            </div>
            <el-switch v-model="form.aiConsent" />
          </div>
          <div class="opt-item">
            <div>
              <div class="opt-title">允许本篇日记纳入后续历史记忆检索</div>
              <div class="muted-text">用于给未来的自己提供更贴合上下文的建议</div>
            </div>
            <el-switch v-model="form.memoryIncluded" />
          </div>
          <div v-if="!isEdit && form.aiConsent" class="opt-item">
            <div>
              <div class="opt-title">情感分析引擎选择</div>
              <div class="muted-text">默认使用高可解释性中文规则引擎，可切换 Python 扩展对照模型</div>
            </div>
            <el-select v-model="form.analyzerMethod" style="width: 210px;">
              <el-option label="规则基线引擎 (RULE_V1)" value="RULE_V1" />
              <el-option label="监督学习对照 (ML_TFIDF)" value="ML_TFIDF" />
              <el-option label="SnowNLP 情感分析" value="SNOWNLP" />
              <el-option label="BERT 扩展接口 (自动回退)" value="BERT" />
            </el-select>
          </div>
          <div v-if="isEdit && form.aiConsent" class="opt-item">
            <div>
              <div class="opt-title">保存修改后立即重算分析与建议</div>
              <div class="muted-text">正文修改将自动递增文本版本号，旧分析结果自动失效</div>
            </div>
            <div style="display: flex; gap: 16px;">
              <el-checkbox v-model="form.reanalyzeNow">重算情绪分析</el-checkbox>
              <el-checkbox v-model="form.regenerateSuggestion">重新生成 AI 建议</el-checkbox>
            </div>
          </div>
        </div>

        <div class="submit-row">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" size="large" :loading="submitting" @click="onSubmit">
            {{ submitting ? '正在保存并分析情绪...' : (isEdit ? '保存修改' : '提交日记并生成分析与建议') }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { EMOTION_OPTIONS } from '@/utils/emotions'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const isEdit = computed(() => !!route.params.id)

const todayStr = new Date().toISOString().slice(0, 10)
const form = reactive({
  title: '',
  content: '',
  entryDate: todayStr,
  selfEmotionTag: '',
  aiConsent: true,
  memoryIncluded: true,
  analyzerMethod: 'RULE_V1',
  reanalyzeNow: true,
  regenerateSuggestion: true
})

const rules = {
  title: [{ required: true, message: '请输入日记标题', trigger: 'blur' }],
  entryDate: [{ required: true, message: '请选择记录日期', trigger: 'change' }],
  content: [{ required: true, message: '请写下今天的日记内容', trigger: 'blur' }]
}

const quickTemplates = [
  {
    title: '赶 DDL 与焦虑梳理',
    content: '这周课程大作业和毕设进度叠在一起，晚上感觉压力有点大、有点焦虑。不过把最紧急的两项拆出来后，稍微明确了一点明天先做什么。',
    tag: 'ANXIETY'
  },
  {
    title: '混合情绪（担心但期待）',
    content: '临近毕业答辩有点担心自己现场被问住，但是看到整个系统已经流畅跑通仍然非常期待展示自己的成果。',
    tag: 'MIXED'
  },
  {
    title: '收获成就感的一天',
    content: '今天卡了两天的后端联调终于全部跑通了，效率很高，心里特别开心也很有成就感，晚上去操场散步奖励了自己。',
    tag: 'JOY'
  }
]

function applyTemplate(tpl) {
  form.title = tpl.title
  form.content = tpl.content
  form.selfEmotionTag = tpl.tag
}

async function onSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await api.updateDiary(route.params.id, form)
      ElMessage.success('日记已更新')
      router.push(`/diaries/${route.params.id}`)
    } else {
      const requestId = `req-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
      const res = await api.createDiary({ ...form, requestId })
      ElMessage.success('日记已保存，分析与建议已就绪')
      router.push(`/diaries/${res.data.id}`)
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const res = await api.getDiary(route.params.id)
    const d = res.data
    form.title = d.title
    form.content = d.content
    form.entryDate = d.entryDate
    form.selfEmotionTag = d.selfEmotionTag || ''
    form.aiConsent = d.aiConsent
    form.memoryIncluded = d.memoryIncluded
  }
})
</script>

<style scoped>
.editor-wrap {
  max-width: 900px;
  margin: 0 auto;
}
.editor-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-soft);
}
.form-row-2 {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}
.mood-selector {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.mood-chip {
  border: 1px solid var(--border-soft);
  background: #fff;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.mood-chip:hover {
  border-color: var(--el-color-primary);
}
.mood-chip.selected {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}
.prompt-templates {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}
.tpl-tag {
  cursor: pointer;
}
.ai-options-box {
  background: var(--bg-page);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}
.opt-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.opt-title {
  font-size: 14px;
  font-weight: 500;
}
.submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
