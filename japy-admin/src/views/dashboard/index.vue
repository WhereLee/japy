<template>
  <div class="dash">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card" :style="{ borderTopColor: card.color }">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- AI 运维入口 -->
    <el-card shadow="never" class="ai-panel">
      <template #header>
        <div class="panel-head">
          <span>AI 运维分析顾问</span>
          <el-tag v-if="aiReport" :type="aiReport.llmAvailable ? 'success' : 'danger'" size="small">
            {{ aiReport.llmAvailable ? 'LLM 在线' : 'LLM 离线' }}
          </el-tag>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="ai-stat">
            <div class="num">{{ aiReport?.eventTotal ?? '-' }}</div>
            <div class="lbl">信号总数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="ai-stat">
            <div class="num warn">{{ aiReport?.pendingEvents ?? '-' }}</div>
            <div class="lbl">待处理信号</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="ai-stat">
            <div class="num">{{ aiReport?.pendingSuggestions ?? '-' }}</div>
            <div class="lbl">待审建议卡</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="ai-stat">
            <div class="num">{{ aiReport?.feedbackTotal ?? '-' }}</div>
            <div class="lbl">反馈总数</div>
          </div>
        </el-col>
      </el-row>
      <div class="ai-actions">
        <el-button type="primary" @click="go('/ai/ops/events')">查看信号</el-button>
        <el-button @click="go('/ai/ops/suggestions')">建议卡</el-button>
        <el-button @click="go('/ai/ops/feedback')">反馈分析</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboard } from '@/api/system'
import { getReport } from '@/api/ai'

const router = useRouter()
const dash = ref<any>({})
const aiReport = ref<any>(null)

const cards = computed(() => [
  { label: '用户总数', value: dash.value.userCount ?? '-', color: '#6366f1' },
  { label: '操作日志', value: dash.value.operLogCount ?? '-', color: '#0ea5e9' },
  { label: '登录日志', value: dash.value.loginLogCount ?? '-', color: '#10b981' },
  { label: '在线用户', value: dash.value.onlineCount ?? '-', color: '#f59e0b' }
])

function go(path: string) {
  router.push(path)
}

onMounted(async () => {
  try {
    dash.value = await getDashboard()
  } catch { /* 部分权限缺失时忽略 */ }
  try {
    aiReport.value = await getReport()
  } catch { /* tech_admin 也可能无 dashboard 权限 */ }
})
</script>

<style scoped>
.stat-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #eef0f4;
  border-top: 3px solid transparent;
  padding: 18px 20px;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}
.stat-label { font-size: 13px; color: #6b7280; }
.stat-value { font-size: 28px; font-weight: 700; margin-top: 6px; }
.ai-panel { margin-top: 16px; border-radius: 12px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
.ai-stat { text-align: center; padding: 10px 0; }
.ai-stat .num { font-size: 30px; font-weight: 700; color: #6366f1; }
.ai-stat .num.warn { color: #f59e0b; }
.ai-stat .lbl { font-size: 13px; color: #6b7280; margin-top: 4px; }
.ai-actions { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #e5e7eb; }
</style>
