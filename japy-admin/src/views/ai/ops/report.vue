<template>
  <div>
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="head">
          <span class="title">监测报告</span>
          <el-button type="primary" size="small" :loading="running" v-perm="'ai:event:run'" @click="onRun">
            <el-icon style="margin-right: 4px"><VideoPlay /></el-icon>手动检测
          </el-button>
        </div>
      </template>

      <el-row :gutter="16" v-if="report">
        <el-col :span="6" v-for="it in items" :key="it.label">
          <div class="metric">
            <div class="m-value" :style="{ color: it.color }">{{ it.value }}</div>
            <div class="m-label">{{ it.label }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="head"><span class="title">最近信号</span></div>
      </template>
      <el-table :data="events" v-loading="loading" stripe>
        <el-table-column prop="monitorName" label="检测项" min-width="130" />
        <el-table-column prop="summary" label="事实描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="confidence" label="置信度" width="90">
          <template #default="{ row }">
            <el-tag :type="confidenceType(row.confidence)" size="small">{{ row.confidence }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import { getReport, listEvents, runMonitor } from '@/api/ai'
import type { ReportData, MonitorEvent } from '@/api/ai'

const report = ref<ReportData | null>(null)
const events = ref<MonitorEvent[]>([])
const loading = ref(false)
const running = ref(false)

const items = computed(() => [
  { label: '信号总数', value: report.value?.eventTotal ?? '-', color: '#6366f1' },
  { label: '待处理信号', value: report.value?.pendingEvents ?? '-', color: '#f59e0b' },
  { label: '待审建议卡', value: report.value?.pendingSuggestions ?? '-', color: '#0ea5e9' },
  { label: '反馈总数', value: report.value?.feedbackTotal ?? '-', color: '#10b981' }
])

const statusMap: Record<number, [string, string]> = {
  0: ['待处理', 'warning'],
  1: ['已确认', 'success'],
  2: ['已确认', 'success'],
  3: ['已忽略', 'info']
}
function statusText(s: number) { return statusMap[s]?.[0] ?? '未知' }
function statusType(s: number) { return statusMap[s]?.[1] ?? 'info' as any }
function confidenceType(c: number) { return c >= 0.8 ? 'danger' : c >= 0.5 ? 'warning' : 'info' as any }

async function onRun() {
  running.value = true
  try {
    const n = await runMonitor()
    ElMessage.success(`检测完成，新增 ${n} 条信号`)
    await Promise.all([load(), loadEvents()])
  } finally {
    running.value = false
  }
}

async function load() {
  try { report.value = await getReport() } catch { /* 忽略 */ }
}
async function loadEvents() {
  loading.value = true
  try {
    const p = await listEvents({ page: 1, size: 8 })
    events.value = p.records
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  loadEvents()
})
</script>

<style scoped>
.page-card { border-radius: 12px; margin-bottom: 16px; }
.head { display: flex; align-items: center; justify-content: space-between; }
.title { font-weight: 600; }
.metric { text-align: center; padding: 12px 0; background: #f8fafc; border-radius: 10px; }
.m-value { font-size: 30px; font-weight: 700; }
.m-label { font-size: 13px; color: #6b7280; margin-top: 4px; }
</style>
