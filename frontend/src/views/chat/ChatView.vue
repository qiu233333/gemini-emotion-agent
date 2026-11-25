<template>
  <div class="chat-layout">
    <!-- 左侧会话栏 -->
    <div class="page-card session-sidebar">
      <el-button type="primary" style="width: 100%; margin-bottom: 12px;" @click="onCreateSession">
        <el-icon><Plus /></el-icon> 新建陪伴对话
      </el-button>
      <div class="mem-toggle">
        <span class="muted-text">新会话关联日记记忆</span>
        <el-switch v-model="newSessionMemory" size="small" />
      </div>

      <div class="session-list">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: activeSession?.id === s.id }"
          @click="selectSession(s)"
        >
          <div class="s-title">{{ s.title }}</div>
          <div class="s-meta">
            <span>{{ s.useDiaryMemory ? '带日记记忆' : '独立对话' }}</span>
            <el-icon class="del-icon" @click.stop="onDeleteSession(s.id)"><Delete /></el-icon>
          </div>
        </div>
        <el-empty v-if="!sessions.length" description="暂无对话，点击上方新建" :image-size="56" />
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="page-card chat-main">
      <div class="disclaimer-bar" style="margin-bottom: 12px; font-size: 12px;">
        AI 陪伴对话仅用于日常倾诉与情绪梳理，不替代专业心理咨询。如遇高风险危机表述，系统将优先提供现实支持热线指引。
      </div>

      <div ref="msgScrollRef" class="msg-list">
        <div v-if="!messages.length" class="welcome-box">
          <h3>你好，欢迎来到心迹陪伴空间</h3>
          <p class="muted-text">
            你可以聊聊最近的学习压力、人际困扰或任何小情绪。我会结合你的对话上下文{{ activeSession?.useDiaryMemory ? '与近期情绪日记摘要' : '' }}给出温和陪伴。
          </p>
          <div class="starter-row">
            <el-button
              v-for="q in starters"
              :key="q"
              size="small"
              plain
              @click="quickSend(q)"
            >
              {{ q }}
            </el-button>
          </div>
        </div>

        <div
          v-for="m in messages"
          :key="m.id"
          class="msg-row"
          :class="m.role === 'USER' ? 'user' : 'assistant'"
        >
          <div class="bubble" :class="{ crisis: m.status === 'HIGH_RISK_SAFE' }">
            <div class="bubble-text">{{ m.content }}</div>
            <div class="bubble-meta">
              <EmotionBadge v-if="m.detectedEmotion" :emotion="m.detectedEmotion" />
              <span v-if="m.role === 'ASSISTANT' && m.modelName" class="muted-text">{{ m.modelName }}</span>
              <span v-if="parseRefs(m.referencedDiaryIds).length" class="muted-text">
                引用日记：
                <el-link
                  v-for="rid in parseRefs(m.referencedDiaryIds)"
                  :key="rid"
                  type="primary"
                  style="font-size: 12px; margin-right: 4px;"
                  @click="router.push(`/diaries/${rid}`)"
                >
                  #{{ rid }}
                </el-link>
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="input-bar">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入你想说的话（Enter 发送，Shift+Enter 换行）"
          @keydown.enter.exact.prevent="onSend"
        />
        <el-button type="primary" :loading="sending" style="height: 54px; padding: 0 22px;" @click="onSend">
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import EmotionBadge from '@/components/EmotionBadge.vue'

const router = useRouter()
const sessions = ref([])
const activeSession = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const newSessionMemory = ref(true)
const msgScrollRef = ref()

const starters = [
  '最近赶毕设和找工作同时进行，感觉很焦虑怎么办？',
  '今天明明完成了任务但还是提不起劲，有点低落',
  '帮我结合我最近的日记，聊聊我这段时间的情绪变化'
]

function parseRefs(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch { return [] }
}

async function scrollToBottom() {
  await nextTick()
  if (msgScrollRef.value) {
    msgScrollRef.value.scrollTop = msgScrollRef.value.scrollHeight
  }
}

async function loadSessions() {
  const res = await api.listChatSessions()
  sessions.value = res.data || []
  if (!activeSession.value && sessions.value.length) {
    await selectSession(sessions.value[0])
  }
}

async function onCreateSession() {
  const res = await api.createChatSession({ useDiaryMemory: newSessionMemory.value })
  sessions.value.unshift(res.data)
  await selectSession(res.data)
}

async function selectSession(session) {
  activeSession.value = session
  const res = await api.listChatMessages(session.id)
  messages.value = res.data || []
  scrollToBottom()
}

async function onDeleteSession(id) {
  await api.deleteChatSession(id)
  sessions.value = sessions.value.filter((s) => s.id !== id)
  if (activeSession.value?.id === id) {
    activeSession.value = sessions.value[0] || null
    messages.value = activeSession.value ? (await api.listChatMessages(activeSession.value.id)).data : []
  }
  ElMessage.success('会话已删除')
}

async function quickSend(text) {
  draft.value = text
  await onSend()
}

async function onSend() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  if (!activeSession.value) {
    await onCreateSession()
  }
  sending.value = true
  draft.value = ''
  try {
    const res = await api.sendChatMessage(activeSession.value.id, text)
    messages.value.push(res.data.userMessage, res.data.assistantMessage)
    await loadSessions()
    scrollToBottom()
  } finally {
    sending.value = false
  }
}

onMounted(loadSessions)
</script>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 270px 1fr;
  gap: 18px;
  height: calc(100vh - 125px);
  min-height: 540px;
}
.session-sidebar {
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}
.mem-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-soft);
}
.session-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.session-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
}
.session-item:hover {
  background: var(--bg-soft);
}
.session-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
.s-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.s-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-secondary);
}
.del-icon:hover {
  color: #d45348;
}
.chat-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.welcome-box {
  margin: auto;
  text-align: center;
  max-width: 540px;
  padding: 20px;
}
.starter-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}
.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}
.bubble {
  max-width: 78%;
  padding: 12px 15px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.msg-row.user .bubble {
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-row.assistant .bubble {
  background: var(--bg-page);
  border: 1px solid var(--border-soft);
  color: var(--text-main);
  border-bottom-left-radius: 4px;
}
.bubble.crisis {
  background: #fff5f5 !important;
  border-color: #f5b8b8 !important;
  color: #7a1c14 !important;
}
.bubble-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 11px;
}
.input-bar {
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-soft);
}
</style>
