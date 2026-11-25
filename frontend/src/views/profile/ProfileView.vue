<template>
  <div class="profile-grid">
    <div class="page-card">
      <h3 class="section-title">个人资料与历史记忆隐私</h3>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input :model-value="auth.user?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="个人简介">
          <el-input v-model="form.bio" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">智能体历史记忆控制</el-divider>

        <el-form-item label="开启近期历史记忆关联">
          <div class="mem-row">
            <span class="muted-text">关闭后，AI 建议和陪伴对话将仅依据当前单条输入生成，不读取历史日记</span>
            <el-switch v-model="form.aiMemoryEnabled" />
          </div>
        </el-form-item>

        <el-form-item :label="`历史记忆检索窗口：最近 ${form.memoryWindowDays} 天`">
          <el-slider v-model="form.memoryWindowDays" :min="7" :max="30" show-stops :step="1" />
        </el-form-item>

        <el-divider content-position="left">修改密码（留空则不修改）</el-divider>
        <div class="pwd-row">
          <el-form-item label="原密码">
            <el-input v-model="form.oldPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" type="password" show-password />
          </el-form-item>
        </div>

        <div style="display: flex; justify-content: space-between; margin-top: 12px;">
          <el-popconfirm
            title="将把你所有已有日记移出历史记忆检索池（不影响原日记保留），确认执行？"
            @confirm="onClearMemory"
          >
            <template #reference>
              <el-button type="warning" plain>一键清除历史记忆关联</el-button>
            </template>
          </el-popconfirm>
          <el-button type="primary" :loading="saving" @click="onSave">保存设置</el-button>
        </div>
      </el-form>
    </div>

    <div class="page-card">
      <h3 class="section-title">提交使用反馈与历史记录</h3>
      <el-form :model="fbForm" label-position="top">
        <el-form-item label="反馈类别">
          <el-select v-model="fbForm.targetType" style="width: 100%;">
            <el-option label="系统整体体验" value="SYSTEM" />
            <el-option label="情绪分析准确性" value="ANALYSIS" />
            <el-option label="AI 调节建议质量" value="SUGGESTION" />
            <el-option label="AI 陪伴对话" value="CHAT" />
            <el-option label="周报/月报" value="REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="满意度评分">
          <el-rate v-model="fbForm.rating" />
        </el-form-item>
        <el-form-item label="反馈说明">
          <el-input v-model="fbForm.comment" type="textarea" :rows="3" placeholder="欢迎提出改进建议或使用中遇到的问题" />
        </el-form-item>
        <el-button type="primary" plain @click="onSubmitFeedback">提交反馈</el-button>
      </el-form>

      <el-divider content-position="left">我的历史反馈</el-divider>
      <div v-if="!myFeedbacks.length" class="muted-text">暂无反馈记录</div>
      <div v-for="f in myFeedbacks" :key="f.id" class="fb-item">
        <div class="fb-top">
          <el-tag size="small">{{ f.targetType }}</el-tag>
          <el-rate :model-value="f.rating" disabled size="small" />
          <el-tag size="small" :type="f.status === 'RESOLVED' ? 'success' : 'warning'">
            {{ f.status === 'RESOLVED' ? '已回复' : '待处理' }}
          </el-tag>
        </div>
        <div class="fb-comment">{{ f.comment || '（无文字补充）' }}</div>
        <div v-if="f.adminReply" class="fb-reply">管理员回复：{{ f.adminReply }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const saving = ref(false)
const myFeedbacks = ref([])

const form = reactive({
  nickname: '',
  email: '',
  bio: '',
  aiMemoryEnabled: true,
  memoryWindowDays: 30,
  oldPassword: '',
  newPassword: ''
})

const fbForm = reactive({
  targetType: 'SYSTEM',
  rating: 5,
  comment: ''
})

async function loadData() {
  await auth.refreshProfile()
  const u = auth.user || {}
  form.nickname = u.nickname || ''
  form.email = u.email || ''
  form.bio = u.bio || ''
  form.aiMemoryEnabled = u.aiMemoryEnabled !== false
  form.memoryWindowDays = u.memoryWindowDays || 30

  const res = await api.myFeedback()
  myFeedbacks.value = res.data || []
}

async function onSave() {
  saving.value = true
  try {
    const payload = {
      nickname: form.nickname,
      email: form.email,
      bio: form.bio,
      aiMemoryEnabled: form.aiMemoryEnabled,
      memoryWindowDays: form.memoryWindowDays
    }
    if (form.newPassword) {
      payload.oldPassword = form.oldPassword
      payload.newPassword = form.newPassword
    }
    const res = await api.updateProfile(payload)
    auth.setSession(auth.token, res.data)
    form.oldPassword = ''
    form.newPassword = ''
    ElMessage.success('个人设置已保存')
  } finally {
    saving.value = false
  }
}

async function onClearMemory() {
  const res = await api.clearMemory()
  ElMessage.success(`已将 ${res.data.clearedDiaryCount} 篇历史日记移出记忆检索池`)
}

async function onSubmitFeedback() {
  await api.submitFeedback(fbForm)
  ElMessage.success('反馈已提交')
  fbForm.comment = ''
  const res = await api.myFeedback()
  myFeedbacks.value = res.data || []
}

onMounted(loadData)
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
  align-items: start;
}
.mem-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
}
.pwd-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.fb-item {
  border: 1px solid var(--border-soft);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.fb-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.fb-comment {
  font-size: 13px;
  color: var(--text-main);
}
.fb-reply {
  margin-top: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--bg-soft);
  font-size: 12px;
  color: var(--el-color-primary);
}
@media (max-width: 960px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
