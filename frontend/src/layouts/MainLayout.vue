<template>
  <div class="layout-shell">
    <aside class="sidebar">
      <div class="brand" @click="router.push('/dashboard')">
        <div class="brand-logo">心</div>
        <div>
          <div class="brand-title">心迹 MoodAgent</div>
          <div class="brand-sub">情绪记录与管理智能体</div>
        </div>
      </div>

      <el-button
        type="primary"
        class="write-btn"
        size="large"
        @click="router.push('/diaries/new')"
      >
        <el-icon><EditPen /></el-icon>
        <span>记录今日情绪</span>
      </el-button>

      <nav class="nav-list">
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>

        <div v-if="auth.isAdmin" class="nav-divider">系统管理</div>
        <router-link
          v-if="auth.isAdmin"
          to="/admin"
          class="nav-item"
          :class="{ active: isActive('/admin') }"
        >
          <el-icon><Setting /></el-icon>
          <span>管理后台</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="llm-pill" :class="llmLive ? 'live' : 'fallback'">
          <span class="dot" />
          <span>{{ llmLive ? `DeepSeek 已连接 (${llmModel})` : 'DeepSeek 待配置 Key (兜底可用)' }}</span>
        </div>
        <div class="footer-note">非医疗日常情绪辅助工具</div>
      </div>
    </aside>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <h2 class="page-heading">{{ currentHeading }}</h2>
        </div>
        <div class="topbar-right">
          <el-tag size="small" :type="auth.user?.aiMemoryEnabled ? 'success' : 'info'" effect="plain">
            历史记忆：{{ auth.user?.aiMemoryEnabled ? `近 ${auth.user?.memoryWindowDays || 30} 天开启` : '已关闭' }}
          </el-tag>
          <el-dropdown @command="handleCommand">
            <div class="user-trigger">
              <div class="avatar">{{ (auth.user?.nickname || 'U').slice(0, 1) }}</div>
              <span class="uname">{{ auth.user?.nickname || auth.user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人设置与记忆隐私</el-dropdown-item>
                <el-dropdown-item v-if="auth.isAdmin" command="admin">进入管理后台</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content-scroll">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = [
  { path: '/dashboard', label: '首页仪表盘', icon: 'HomeFilled' },
  { path: '/diaries', label: '情绪日记', icon: 'Notebook' },
  { path: '/stats', label: '情绪趋势与日历', icon: 'TrendCharts' },
  { path: '/chat', label: 'AI 陪伴对话', icon: 'ChatDotRound' },
  { path: '/reports', label: '周报与月报', icon: 'Document' },
  { path: '/nlp-lab', label: 'NLP 算法对照', icon: 'Cpu' },
  { path: '/profile', label: '隐私与个人设置', icon: 'User' }
]

const headingMap = {
  Dashboard: '首页仪表盘',
  DiaryList: '情绪日记档案',
  DiaryNew: '记录一篇情绪日记',
  DiaryEdit: '编辑情绪日记',
  DiaryDetail: '情绪分析与智能体建议',
  Stats: '情绪趋势、分布与日历',
  Chat: 'AI 陪伴多轮对话',
  Reports: '周期情绪周报 / 月报',
  NlpLab: 'NLP 多模型情感分析对照实验',
  Profile: '个人信息与历史记忆隐私',
  Admin: '系统管理后台（隐私隔离视图）'
}

const currentHeading = computed(() => headingMap[route.name] || '心迹情绪管理')
const llmLive = computed(() => !!auth.systemInfo?.llmLiveEnabled)
const llmModel = computed(() => auth.systemInfo?.llmModel || 'deepseek-chat')

function isActive(path) {
  return route.path === path || (path === '/diaries' && route.path.startsWith('/diaries'))
}

function handleCommand(cmd) {
  if (cmd === 'profile') router.push('/profile')
  else if (cmd === 'admin') router.push('/admin')
  else if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}

onMounted(() => {
  auth.fetchSystemInfo()
  auth.refreshProfile().catch(() => {})
})
</script>

<style scoped>
.layout-shell {
  display: flex;
  min-height: 100vh;
}
.sidebar {
  width: 248px;
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid var(--border-soft);
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 6px 18px;
  cursor: pointer;
}
.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2b6e6c, #4da39d);
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(43, 110, 108, 0.25);
}
.brand-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}
.brand-sub {
  font-size: 12px;
  color: var(--text-secondary);
}
.write-btn {
  width: 100%;
  border-radius: 10px;
  margin-bottom: 16px;
  font-weight: 600;
}
.nav-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
}
.nav-divider {
  font-size: 11px;
  color: #8b9c9a;
  padding: 14px 12px 6px;
  letter-spacing: 0.5px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.16s ease;
}
.nav-item:hover {
  background: var(--bg-soft);
  color: var(--el-color-primary);
}
.nav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.sidebar-footer {
  padding-top: 14px;
  border-top: 1px solid var(--border-soft);
}
.llm-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  padding: 6px 10px;
  border-radius: 8px;
  margin-bottom: 6px;
}
.llm-pill.live {
  background: #eaf7f0;
  color: #21734c;
}
.llm-pill.fallback {
  background: #f3f6f6;
  color: #5a6c6a;
}
.llm-pill .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}
.footer-note {
  font-size: 11px;
  color: #8b9c9a;
  text-align: center;
}
.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 62px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border-soft);
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 20;
}
.page-heading {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 10px;
}
.user-trigger:hover {
  background: var(--bg-soft);
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.uname {
  font-size: 14px;
  font-weight: 500;
}
.content-scroll {
  padding: 22px 28px 36px;
  max-width: 1360px;
  width: 100%;
  margin: 0 auto;
}
</style>
