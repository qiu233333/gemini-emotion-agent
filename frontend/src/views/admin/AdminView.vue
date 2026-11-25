<template>
  <div>
    <div class="disclaimer-bar" style="margin-bottom: 18px;">
      <el-icon style="margin-top: 2px;"><Lock /></el-icon>
      <span>{{ overview.privacyNotice || '管理员后台遵循最小权限与隐私保护原则，默认不展示任何用户的私人情绪日记正文或聊天原文。' }}</span>
    </div>

    <el-tabs v-model="activeTab" class="page-card">
      <!-- Tab 1: 运行统计总览 -->
      <el-tab-pane label="系统运行总览" name="overview">
        <div class="kpi-grid">
          <div class="mini-kpi">
            <div class="muted-text">注册用户 / 活跃</div>
            <div class="val">{{ overview.totalUsers || 0 }} / {{ overview.activeUsers || 0 }}</div>
          </div>
          <div class="mini-kpi">
            <div class="muted-text">累计日记篇数</div>
            <div class="val">{{ overview.totalDiaries || 0 }}</div>
          </div>
          <div class="mini-kpi">
            <div class="muted-text">AI 建议 / 高风险防护触发</div>
            <div class="val">{{ overview.totalSuggestions || 0 }} / {{ overview.highRiskAlerts || 0 }}</div>
          </div>
          <div class="mini-kpi">
            <div class="muted-text">生效风控规则 / 待处理反馈</div>
            <div class="val">{{ overview.enabledRules || 0 }} / {{ overview.pendingFeedbacks || 0 }}</div>
          </div>
        </div>
        <div style="margin-top: 20px;">
          <h4 class="section-title">全站脱敏情绪标签聚合分布</h4>
          <div ref="adminPieRef" style="width: 100%; height: 300px;" />
        </div>
      </el-tab-pane>

      <!-- Tab 2: 用户管理 -->
      <el-tab-pane label="用户与额度管理" name="users">
        <div style="display: flex; gap: 10px; margin-bottom: 14px;">
          <el-input v-model="userKeyword" placeholder="搜索用户名或昵称" clearable style="width: 240px;" @keyup.enter="loadUsers" />
          <el-button type="primary" @click="loadUsers">查询</el-button>
        </div>
        <el-table :data="users" border stripe>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户名" width="130" />
          <el-table-column prop="nickname" label="昵称" width="150" />
          <el-table-column prop="emailMasked" label="脱敏邮箱" width="170" />
          <el-table-column prop="role" label="角色" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : 'info'">{{ row.role }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="diaryCount" label="日记数" width="85" />
          <el-table-column prop="dailyAiLimit" label="每日AI额度" width="110" />
          <el-table-column prop="status" label="状态" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="170">
            <template #default="{ row }">
              <el-button
                v-if="row.id !== auth.user?.id"
                size="small"
                :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                plain
                @click="toggleUserStatus(row)"
              >
                {{ row.status === 'ACTIVE' ? '禁用账号' : '恢复启用' }}
              </el-button>
              <span v-else class="muted-text">当前管理员</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 3: 风险规则与敏感词 -->
      <el-tab-pane label="风险规则与敏感词" name="risk">
        <div style="margin-bottom: 14px;">
          <el-button type="primary" @click="openRuleDialog(null)">新增风险规则</el-button>
        </div>
        <el-table :data="riskRules" border stripe>
          <el-table-column prop="name" label="规则名称" width="160" />
          <el-table-column prop="severity" label="等级" width="85">
            <template #default="{ row }">
              <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : 'warning'">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="matchType" label="类型" width="95" />
          <el-table-column prop="pattern" label="匹配模式" min-width="200" show-overflow-tooltip />
          <el-table-column prop="safeTemplate" label="安全提示模板" min-width="240" show-overflow-tooltip />
          <el-table-column prop="enabled" label="启用" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="openRuleDialog(row)">编辑</el-button>
              <el-popconfirm title="确定删除该风险规则？" @confirm="onDeleteRule(row.id)">
                <template #reference>
                  <el-button size="small" type="danger" plain>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- Tab 4: 用户反馈与脱敏审计 -->
      <el-tab-pane label="用户反馈处理" name="feedback">
        <el-table :data="feedbacks" border stripe>
          <el-table-column prop="username" label="用户" width="120" />
          <el-table-column prop="targetType" label="对象" width="110" />
          <el-table-column prop="rating" label="评分" width="80" />
          <el-table-column prop="comment" label="反馈内容" min-width="220" />
          <el-table-column prop="status" label="状态" width="95">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'RESOLVED' ? 'success' : 'warning'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="adminReply" label="回复" min-width="180" />
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button size="small" type="primary" plain @click="onResolveFeedback(row)">
                {{ row.status === 'RESOLVED' ? '修改回复' : '回复处理' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="脱敏审计日志" name="audit">
        <el-table :data="auditLogs" border stripe max-height="460">
          <el-table-column prop="createdAt" label="时间" width="175" />
          <el-table-column prop="actorUserId" label="操作人ID" width="90" />
          <el-table-column prop="actorRole" label="角色" width="85" />
          <el-table-column prop="action" label="动作" width="160" />
          <el-table-column prop="resourceType" label="资源" width="110" />
          <el-table-column prop="summaryMasked" label="脱敏摘要（严禁含正文）" min-width="280" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 风险规则编辑弹窗 -->
    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑风险规则' : '新增风险规则'" width="560px">
      <el-form :model="ruleForm" label-position="top">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.name" />
        </el-form-item>
        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px;">
          <el-form-item label="匹配类型">
            <el-select v-model="ruleForm.matchType" style="width: 100%;">
              <el-option label="REGEX (正则)" value="REGEX" />
              <el-option label="KEYWORD (关键词)" value="KEYWORD" />
            </el-select>
          </el-form-item>
          <el-form-item label="严重等级">
            <el-select v-model="ruleForm.severity" style="width: 100%;">
              <el-option label="HIGH (高风险短路)" value="HIGH" />
              <el-option label="LOW (温和关怀)" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item label="类别">
            <el-input v-model="ruleForm.category" />
          </el-form-item>
        </div>
        <el-form-item label="匹配词或正则表达式">
          <el-input v-model="ruleForm.pattern" placeholder="例如：自杀|轻生|不想活" />
        </el-form-item>
        <el-form-item label="命中时的支持性安全提示模板">
          <el-input v-model="ruleForm.safeTemplate" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="onSaveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { api } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { getEmotionMeta } from '@/utils/emotions'

const auth = useAuthStore()
const activeTab = ref('overview')
const overview = ref({})
const users = ref([])
const userKeyword = ref('')
const riskRules = ref([])
const feedbacks = ref([])
const auditLogs = ref([])
const adminPieRef = ref()
let pieChart = null

const ruleDialog = ref(false)
const ruleForm = reactive({
  id: null,
  name: '',
  pattern: '',
  matchType: 'REGEX',
  severity: 'HIGH',
  category: '危机干预',
  safeTemplate: '',
  enabled: true
})

async function loadAll() {
  const [ov, us, rr, fb, al] = await Promise.all([
    api.adminOverview(),
    api.adminUsers(),
    api.adminRiskRules(),
    api.adminFeedback(),
    api.adminAuditLogs(80)
  ])
  overview.value = ov.data || {}
  users.value = us.data || []
  riskRules.value = rr.data || []
  feedbacks.value = fb.data || []
  auditLogs.value = al.data || []
  await nextTick()
  renderAdminPie()
}

function renderAdminPie() {
  if (!adminPieRef.value) return
  if (!pieChart) pieChart = echarts.init(adminPieRef.value)
  const dist = overview.value.globalEmotionDistribution || {}
  pieChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['42%', '70%'],
        center: ['50%', '45%'],
        data: Object.entries(dist).map(([k, v]) => ({
          name: `${getEmotionMeta(k).icon} ${getEmotionMeta(k).label}`,
          value: v,
          itemStyle: { color: getEmotionMeta(k).color }
        }))
      }
    ]
  })
}

async function loadUsers() {
  const res = await api.adminUsers(userKeyword.value)
  users.value = res.data || []
}

async function toggleUserStatus(row) {
  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await api.adminUpdateUserStatus(row.id, { status: nextStatus })
  ElMessage.success(`已将用户 ${row.username} 状态更新为 ${nextStatus}`)
  loadUsers()
}

function openRuleDialog(row) {
  if (row) {
    Object.assign(ruleForm, row)
  } else {
    Object.assign(ruleForm, {
      id: null,
      name: '',
      pattern: '',
      matchType: 'REGEX',
      severity: 'HIGH',
      category: '危机干预',
      safeTemplate: '',
      enabled: true
    })
  }
  ruleDialog.value = true
}

async function onSaveRule() {
  if (ruleForm.id) {
    await api.adminUpdateRiskRule(ruleForm.id, ruleForm)
  } else {
    await api.adminCreateRiskRule(ruleForm)
  }
  ruleDialog.value = false
  ElMessage.success('风险规则已保存')
  const rr = await api.adminRiskRules()
  riskRules.value = rr.data || []
}

async function onDeleteRule(id) {
  await api.adminDeleteRiskRule(id)
  ElMessage.success('风险规则已删除')
  const rr = await api.adminRiskRules()
  riskRules.value = rr.data || []
}

async function onResolveFeedback(row) {
  const { value } = await ElMessageBox.prompt('请输入给用户的处理回复：', '反馈处理', {
    inputValue: row.adminReply || '感谢反馈，我们已记录并持续优化体验！'
  })
  await api.adminResolveFeedback(row.id, value)
  ElMessage.success('反馈已标记为已处理')
  const fb = await api.adminFeedback()
  feedbacks.value = fb.data || []
}

onMounted(loadAll)
onBeforeUnmount(() => pieChart?.dispose())
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.mini-kpi {
  background: var(--bg-page);
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 16px;
}
.mini-kpi .val {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
</style>
