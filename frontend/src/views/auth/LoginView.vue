<template>
  <div class="login-page">
    <div class="login-wrapper">
      <div class="hero-panel">
        <div class="hero-badge">基于大语言模型与轻量级智能体</div>
        <h1 class="hero-title">心迹 · 情绪记录与管理智能体系统</h1>
        <p class="hero-desc">
          面向大学生日常学习与生活节奏的情绪梳理空间。打通“情绪日记记录 — 中文 NLP 分析 — 近期历史记忆 — DeepSeek 温和建议 — 长期趋势可视化”完整闭环。
        </p>

        <div class="feature-grid">
          <div class="feat">
            <div class="feat-title">结构化中文情感分析</div>
            <div class="feat-sub">支持否定词、转折混合情绪识别、关键词提取与用户手动纠偏</div>
          </div>
          <div class="feat">
            <div class="feat-title">受控历史记忆与可追溯建议</div>
            <div class="feat-sub">结合近 7–30 天摘要生成 DeepSeek 温和建议并标注引用日记编号</div>
          </div>
          <div class="feat">
            <div class="feat-title">严谨 ECharts 趋势与情绪日历</div>
            <div class="feat-sub">缺失日期留空不误填，支持按日期与关键词回看原始记录</div>
          </div>
          <div class="feat">
            <div class="feat-title">安全边界与隐私隔离</div>
            <div class="feat-sub">高风险输入优先触发支持性提示，后台管理默认不暴露私人正文</div>
          </div>
        </div>

        <div class="hero-disclaimer">
          非医疗定位声明：本系统仅作为情绪记录、自我回顾与日常陪伴辅助工具，不提供心理疾病诊断或医疗治疗服务。
        </div>
      </div>

      <div class="form-panel">
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="账号登录" name="login">
            <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top" @submit.prevent="onLogin">
              <el-form-item label="用户名" prop="username">
                <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" size="large" prefix-icon="Lock" />
              </el-form-item>
              <el-button type="primary" size="large" :loading="loading" class="submit-btn" native-type="submit">
                登录系统
              </el-button>
            </el-form>

            <div class="demo-box">
              <div class="demo-title">答辩演示快捷填入</div>
              <div class="demo-actions">
                <el-button size="small" plain @click="fillDemo('student', 'Student@123')">
                  演示学生账号 (student)
                </el-button>
                <el-button size="small" plain @click="fillDemo('admin', 'Admin@123')">
                  管理员账号 (admin)
                </el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="新用户注册" name="register">
            <el-form ref="regRef" :model="regForm" :rules="regRules" label-position="top" @submit.prevent="onRegister">
              <el-form-item label="用户名" prop="username">
                <el-input v-model="regForm.username" placeholder="3-32位字母或数字" size="large" />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="regForm.nickname" placeholder="希望系统如何称呼你" size="large" />
              </el-form-item>
              <el-form-item label="邮箱（选填）" prop="email">
                <el-input v-model="regForm.email" placeholder="name@example.com" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="regForm.password" type="password" show-password placeholder="至少6位密码" size="large" />
              </el-form-item>
              <el-button type="primary" size="large" :loading="loading" class="submit-btn" native-type="submit">
                注册并进入
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const activeTab = ref('login')
const loading = ref(false)
const loginRef = ref()
const regRef = ref()

const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', email: '', password: '' })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const regRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '长度 3-32 个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

function fillDemo(username, password) {
  loginForm.username = username
  loginForm.password = password
}

async function onLogin() {
  await loginRef.value.validate()
  loading.value = true
  try {
    const res = await api.login(loginForm)
    auth.setSession(res.data.token, res.data.user)
    ElMessage.success(`欢迎回来，${res.data.user.nickname}`)
    router.push(res.data.user.role === 'ADMIN' ? '/admin' : '/dashboard')
  } finally {
    loading.value = false
  }
}

async function onRegister() {
  await regRef.value.validate()
  loading.value = true
  try {
    const res = await api.register(regForm)
    auth.setSession(res.data.token, res.data.user)
    ElMessage.success('注册成功，已自动登录')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: radial-gradient(circle at 12% 18%, #e2f1ef 0%, #f5f7f6 48%, #edf2f6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
}
.login-wrapper {
  width: 100%;
  max-width: 1060px;
  display: grid;
  grid-template-columns: 1.25fr 0.95fr;
  background: #ffffff;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--border-soft);
  box-shadow: 0 20px 50px rgba(31, 45, 44, 0.08);
}
.hero-panel {
  background: linear-gradient(155deg, #1f5452 0%, #2b6e6c 55%, #3c8a85 100%);
  color: #f3faf9;
  padding: 44px 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.hero-badge {
  display: inline-block;
  align-self: flex-start;
  background: rgba(255, 255, 255, 0.16);
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.4px;
}
.hero-title {
  font-size: 28px;
  line-height: 1.3;
  margin: 16px 0 12px;
  font-weight: 700;
}
.hero-desc {
  font-size: 14px;
  line-height: 1.7;
  color: rgba(243, 250, 249, 0.88);
  margin: 0 0 24px;
}
.feature-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.feat {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 12px;
  padding: 13px 14px;
}
.feat-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}
.feat-sub {
  font-size: 12px;
  line-height: 1.45;
  color: rgba(243, 250, 249, 0.8);
}
.hero-disclaimer {
  margin-top: 24px;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  font-size: 12px;
  color: rgba(243, 250, 249, 0.78);
}
.form-panel {
  padding: 40px 36px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
  border-radius: 10px;
  font-weight: 600;
}
.demo-box {
  margin-top: 24px;
  padding: 14px;
  background: var(--bg-page);
  border-radius: 12px;
  border: 1px dashed var(--border-soft);
}
.demo-title {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 10px;
}
.demo-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
@media (max-width: 860px) {
  .login-wrapper {
    grid-template-columns: 1fr;
  }
}
</style>
